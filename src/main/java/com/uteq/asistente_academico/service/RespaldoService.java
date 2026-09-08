package com.uteq.asistente_academico.service;

import com.uteq.asistente_academico.dto.RespaldoInfo;
import com.uteq.asistente_academico.dto.RespaldoResumen;
import com.uteq.asistente_academico.dto.RestauracionInfo;
import com.uteq.asistente_academico.exception.RespaldoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Respaldo y restauracion administrativa de la base de datos (pg_dump/
 * pg_restore). Corre con las credenciales del OWNER (postgres), no con
 * el rol acotado app_academico que usa el resto de la app -- ver
 * application.properties (admin.backup.db.*) para el porque.
 *
 * La restauracion SIEMPRE va a una base alterna (asistente_academico_
 * restore), nunca sobre la base real -- se recrea desde cero en cada
 * restauracion para no arrastrar basura de una anterior.
 */
@Service
public class RespaldoService {

    private static final Logger log = LoggerFactory.getLogger(RespaldoService.class);
    private static final DateTimeFormatter FORMATO_NOMBRE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
    private static final Pattern PATRON_NOMBRE_ARCHIVO =
            Pattern.compile("^asistente_academico_\\d{4}-\\d{2}-\\d{2}_\\d{4}\\.dump$");
    private static final String BASE_ALTERNA = "asistente_academico_restore";

    @Value("${admin.backup.db.host}")
    private String host;

    @Value("${admin.backup.db.port}")
    private String port;

    @Value("${admin.backup.db.name}")
    private String nombreBase;

    @Value("${admin.backup.db.user}")
    private String usuario;

    @Value("${admin.backup.db.password}")
    private String password;

    @Value("${admin.backup.storage.path}")
    private String storagePath;

    public RespaldoInfo crearRespaldo() {
        Path directorio = Paths.get(storagePath);
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-directorio-respaldo",
                    "Error al preparar el respaldo", "No se pudo crear el directorio de respaldos.");
        }

        String nombreArchivo = "asistente_academico_" + LocalDateTime.now().format(FORMATO_NOMBRE) + ".dump";
        Path destino = directorio.resolve(nombreArchivo);

        ResultadoProceso resultado = ejecutar(
                "pg_dump", "-h", host, "-p", port, "-U", usuario, "-d", nombreBase, "-Fc", "-f", destino.toString());

        if (resultado.codigoSalida() != 0) {
            log.warn("BACKUP_ERROR | codigoSalida={} | timestamp={}", resultado.codigoSalida(), Instant.now());
            // pg_dump con -f crea/trunca el archivo de destino ANTES de
            // conectarse -- si despues falla, no debe quedar un .dump
            // vacio o a medio escribir confundiendose con un respaldo
            // valido en el listado.
            try {
                Files.deleteIfExists(destino);
            } catch (IOException ignored) {
                // No se relanza: prioridad es reportar el error real de
                // pg_dump, no un problema secundario de limpieza.
            }
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "pg-dump-fallo",
                    "El respaldo falló",
                    "pg_dump terminó con código " + resultado.codigoSalida() + ": " + resultado.errorSalida().trim());
        }

        long tamanoBytes;
        try {
            tamanoBytes = Files.size(destino);
        } catch (IOException e) {
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-tamano-respaldo",
                    "Respaldo generado con advertencia",
                    "El respaldo se generó pero no se pudo leer su tamaño.");
        }

        log.info("BACKUP_EXITO | archivo={} | tamanoBytes={} | timestamp={}", nombreArchivo, tamanoBytes, Instant.now());
        return new RespaldoInfo(nombreArchivo, tamanoBytes);
    }

    public List<RespaldoResumen> listarRespaldos() {
        Path directorio = Paths.get(storagePath);
        if (!Files.isDirectory(directorio)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.list(directorio)) {
            return archivos
                    .filter(p -> p.getFileName().toString().endsWith(".dump"))
                    .map(this::aResumen)
                    .sorted(Comparator.comparing(RespaldoResumen::fechaModificacion).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-listar-respaldos",
                    "Error al listar respaldos", "No se pudo leer el directorio de respaldos.");
        }
    }

    /**
     * Restaura nombreArchivo en la base alterna (nunca en la real),
     * recreandola desde cero, y devuelve un conteo basico (usuarios)
     * como confirmacion de que trajo datos reales.
     */
    public RestauracionInfo restaurar(String nombreArchivo) {
        Path archivo = resolverArchivoValidado(nombreArchivo);
        recrearBaseAlterna();
        ejecutarPgRestore(archivo);
        long conteoUsuarios = contarUsuariosEnBaseAlterna();
        log.info("RESTAURACION_EXITO | archivo={} | conteoUsuarios={} | timestamp={}",
                nombreArchivo, conteoUsuarios, Instant.now());
        return new RestauracionInfo(nombreArchivo, conteoUsuarios);
    }

    /**
     * Resuelve el archivo para descarga -- misma validacion (regex +
     * contencion de ruta) que usa restaurar(), factorizada en
     * resolverArchivoValidado() para no duplicarla.
     */
    public Path obtenerArchivoParaDescarga(String nombreArchivo) {
        return resolverArchivoValidado(nombreArchivo);
    }

    /**
     * Doble validacion contra path traversal: (1) el nombre tiene que
     * matchear exactamente el formato que genera crearRespaldo() (sin
     * "/", "\" ni ".." posibles en ese patron); (2) igual se resuelve
     * la ruta y se confirma que el resultado sigue estando DENTRO del
     * directorio de respaldos, como defensa en profundidad.
     */
    private Path resolverArchivoValidado(String nombreArchivo) {
        if (nombreArchivo == null || !PATRON_NOMBRE_ARCHIVO.matcher(nombreArchivo).matches()) {
            throw new RespaldoException(HttpStatus.BAD_REQUEST, "nombre-archivo-invalido",
                    "Nombre de archivo inválido",
                    "El nombre de archivo no tiene un formato válido de respaldo.");
        }

        Path directorio = Paths.get(storagePath).toAbsolutePath().normalize();
        Path archivo = directorio.resolve(nombreArchivo).normalize();
        if (!archivo.getParent().equals(directorio)) {
            throw new RespaldoException(HttpStatus.BAD_REQUEST, "nombre-archivo-invalido",
                    "Nombre de archivo inválido",
                    "El nombre de archivo no tiene un formato válido de respaldo.");
        }

        if (!Files.isRegularFile(archivo)) {
            throw new RespaldoException(HttpStatus.NOT_FOUND, "respaldo-no-encontrado",
                    "Respaldo no encontrado",
                    "No existe un respaldo con el nombre " + nombreArchivo + ".");
        }
        return archivo;
    }

    private void recrearBaseAlterna() {
        ResultadoProceso drop = ejecutar("dropdb", "-h", host, "-p", port, "-U", usuario, "--if-exists", BASE_ALTERNA);
        if (drop.codigoSalida() != 0) {
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-drop-base-alterna",
                    "Error al recrear la base alterna",
                    "dropdb terminó con código " + drop.codigoSalida() + ": " + drop.errorSalida().trim());
        }

        ResultadoProceso create = ejecutar("createdb", "-h", host, "-p", port, "-U", usuario, "-T", "template0", BASE_ALTERNA);
        if (create.codigoSalida() != 0) {
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-create-base-alterna",
                    "Error al recrear la base alterna",
                    "createdb terminó con código " + create.codigoSalida() + ": " + create.errorSalida().trim());
        }
    }

    private void ejecutarPgRestore(Path archivo) {
        ResultadoProceso resultado = ejecutar(
                "pg_restore", "-h", host, "-p", port, "-U", usuario, "-d", BASE_ALTERNA, archivo.toString());
        if (resultado.codigoSalida() != 0) {
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "pg-restore-fallo",
                    "La restauración falló",
                    "pg_restore terminó con código " + resultado.codigoSalida() + ": " + resultado.errorSalida().trim());
        }
    }

    /**
     * Conexion JDBC puntual (no la del EntityManager de Spring, que
     * apunta a la base real con el rol acotado) contra la base alterna,
     * solo para la verificacion basica post-restauracion.
     */
    private long contarUsuariosEnBaseAlterna() {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + BASE_ALTERNA;
        try (Connection conexion = DriverManager.getConnection(url, usuario, password);
             Statement statement = conexion.createStatement();
             ResultSet resultado = statement.executeQuery("SELECT COUNT(*) FROM usuarios")) {
            resultado.next();
            return resultado.getLong(1);
        } catch (SQLException e) {
            // Nunca se incluye e.getMessage() en la excepcion: no hace
            // falta razonar caso por caso si un mensaje JDBC podria
            // llegar a ecoar la contrasena, alcanza con no propagarlo.
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-verificacion-restauracion",
                    "Restauración sin verificar",
                    "La base se restauró pero no se pudo verificar el conteo de usuarios.");
        }
    }

    private RespaldoResumen aResumen(Path archivo) {
        try {
            BasicFileAttributes atributos = Files.readAttributes(archivo, BasicFileAttributes.class);
            return new RespaldoResumen(
                    archivo.getFileName().toString(),
                    atributos.size(),
                    atributos.lastModifiedTime().toInstant()
            );
        } catch (IOException e) {
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-metadatos-respaldo",
                    "Error al listar respaldos",
                    "No se pudieron leer los metadatos de " + archivo.getFileName() + ".");
        }
    }

    /**
     * Corre un comando externo (pg_dump/pg_restore/dropdb/createdb) con
     * PGPASSWORD SOLO en el entorno del proceso hijo -- nunca como
     * argumento (visible via ps/logs de proceso) ni en ningun log. Los
     * mensajes de error de Postgres para fallos de autenticacion dicen
     * "password authentication failed for user X", nunca el valor real
     * de la clave, asi que devolver errorSalida tal cual en el
     * ProblemDetail del llamador es seguro.
     */
    private ResultadoProceso ejecutar(String... comando) {
        ProcessBuilder procesoBuilder = new ProcessBuilder(comando);
        procesoBuilder.environment().put("PGPASSWORD", password);

        Process proceso;
        try {
            proceso = procesoBuilder.start();
        } catch (IOException e) {
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-iniciar-proceso",
                    "Error al iniciar el proceso", "No se pudo iniciar " + comando[0] + ".");
        }

        try {
            String errorSalida = new String(proceso.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int codigoSalida = proceso.waitFor();
            return new ResultadoProceso(codigoSalida, errorSalida);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RespaldoException(HttpStatus.INTERNAL_SERVER_ERROR, "error-esperando-proceso",
                    "Error al esperar el proceso", "Se interrumpió la espera de " + comando[0] + ".");
        }
    }

    private record ResultadoProceso(int codigoSalida, String errorSalida) {
    }
}
