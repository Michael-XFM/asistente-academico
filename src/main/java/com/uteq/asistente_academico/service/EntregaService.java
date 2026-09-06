package com.uteq.asistente_academico.service;

import com.uteq.asistente_academico.audit.Auditado;
import com.uteq.asistente_academico.dto.EstadoEntregaEstudiante;
import com.uteq.asistente_academico.entity.Entrega;
import com.uteq.asistente_academico.entity.Materia;
import com.uteq.asistente_academico.entity.Matricula;
import com.uteq.asistente_academico.entity.Tarea;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.exception.EntregaException;
import com.uteq.asistente_academico.repository.EntregaRepository;
import com.uteq.asistente_academico.repository.MatriculaRepository;
import com.uteq.asistente_academico.repository.TareaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reglas de negocio de entregas: subir un archivo para una tarea, listar
 * el estado de entrega de una materia (vista del profesor), servir un
 * archivo para descarga, y calificar una entrega. Centralizado aca (no en
 * el controlador, a diferencia de ProfesorController/TareaController) para
 * mantener junta la validacion de archivo + filesystem + reglas de acceso.
 */
@Service
public class EntregaService {

    // Bloque de entregas: unicos formatos aceptados y tamano maximo (debe
    // coincidir con spring.servlet.multipart.max-file-size en
    // application.properties -- ese limite corta la request ANTES de
    // llegar aca; este de aca es la validacion de negocio explicita que
    // pide un mensaje claro en vez de dejar que Spring devuelva el 400 del
    // multipart resolver primero).
    private static final Set<String> FORMATOS_PERMITIDOS = Set.of("PDF", "DOCX", "XLSX");
    private static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;

    @Autowired
    private EntregaRepository entregaRepository;

    @Autowired
    private TareaRepository tareaRepository;

    @Autowired
    private MatriculaRepository matriculaRepository;

    @Value("${entregas.storage.path}")
    private String storagePath;

    /**
     * Sube (o reemplaza) la entrega de un estudiante para una tarea.
     * El rol ESTUDIANTE ya lo garantiza @PreAuthorize en el controlador,
     * por eso no se revalida aca (mismo criterio que ProfesorController
     * con PROFESOR).
     */
    @Auditado
    public Entrega subir(Integer idTarea, Usuario estudiante, MultipartFile archivo) {
        Tarea tarea = tareaRepository.findById(idTarea)
                .orElseThrow(() -> new EntregaException(HttpStatus.NOT_FOUND, "tarea-no-encontrada",
                        "Tarea no encontrada", "No existe una tarea con id " + idTarea + "."));

        if (!matriculaRepository.existsByUsuario_IdUsuarioAndMateria_IdMateria(
                estudiante.getIdUsuario(), tarea.getMateria().getIdMateria())) {
            throw new EntregaException(HttpStatus.FORBIDDEN, "no-matriculado",
                    "Acceso prohibido", "No estás matriculado en la materia de esta tarea.");
        }

        if (estaVencida(tarea)) {
            throw new EntregaException(HttpStatus.CONFLICT, "tarea-vencida",
                    "Tarea vencida",
                    "La fecha y hora límite de esta tarea ya pasó; ya no se pueden subir entregas.");
        }

        String extension = obtenerExtension(archivo.getOriginalFilename());
        if (!FORMATOS_PERMITIDOS.contains(extension)) {
            throw new EntregaException(HttpStatus.BAD_REQUEST, "formato-no-permitido",
                    "Formato no permitido", "Solo se aceptan archivos PDF, DOCX o XLSX.");
        }

        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new EntregaException(HttpStatus.BAD_REQUEST, "tamano-excedido",
                    "Archivo demasiado grande", "El archivo supera el tamaño máximo permitido (10MB).");
        }

        Optional<Entrega> existenteOpt = entregaRepository
                .findByTarea_IdTareaAndEstudiante_IdUsuario(idTarea, estudiante.getIdUsuario());

        if (existenteOpt.isPresent() && existenteOpt.get().getCalificacion() != null) {
            throw new EntregaException(HttpStatus.CONFLICT, "entrega-ya-calificada",
                    "Entrega ya calificada",
                    "Esta entrega ya fue calificada y no se puede reemplazar. Contactá a tu profesor si necesitás corregirla.");
        }

        // Se escribe el archivo nuevo ANTES de borrar el viejo o tocar la
        // fila: si algo falla al guardar en disco, la entrega anterior
        // (fila + archivo fisico) queda intacta en vez de perderse.
        String rutaNueva = guardarArchivoFisico(archivo, idTarea, estudiante.getIdUsuario(), extension);

        Entrega entrega;
        if (existenteOpt.isPresent()) {
            entrega = existenteOpt.get();
            borrarArchivoFisico(entrega.getRutaArchivo());
        } else {
            entrega = new Entrega();
            entrega.setTarea(tarea);
            entrega.setEstudiante(estudiante);
        }
        entrega.setNombreArchivo(archivo.getOriginalFilename());
        entrega.setRutaArchivo(rutaNueva);
        entrega.setFormato(extension);
        entrega.setTamanoBytes(archivo.getSize());
        entrega.setFechaEnvio(LocalDateTime.now());

        return entregaRepository.save(entrega);
    }

    /**
     * Estado de entrega de TODOS los matriculados en la materia de la
     * tarea, para el listado del profesor. 403 si la materia no es del
     * profesor autenticado (no 404, mismo criterio que ProfesorController).
     */
    public List<EstadoEntregaEstudiante> listarPorTarea(Integer idTarea, Usuario profesor) {
        Tarea tarea = tareaRepository.findById(idTarea)
                .orElseThrow(() -> new EntregaException(HttpStatus.NOT_FOUND, "tarea-no-encontrada",
                        "Tarea no encontrada", "No existe una tarea con id " + idTarea + "."));

        validarMateriaPropia(tarea.getMateria(), profesor);

        List<Matricula> matriculados = matriculaRepository.findByMateria_IdMateria(tarea.getMateria().getIdMateria());

        return matriculados.stream()
                .map(Matricula::getUsuario)
                .map(est -> mapAEstado(est, idTarea))
                .collect(Collectors.toList());
    }

    /**
     * Estado de entrega del estudiante autenticado (no de todos los
     * matriculados) para una tarea puntual -- lo que necesita el propio
     * estudiante para saber si ya entrego, con que archivo, y si ya lo
     * calificaron, antes de decidir si subir o reemplazar. 403 si no esta
     * matriculado (mismo mensaje que subir()), nunca 404 solo porque
     * todavia no entrego: eso se representa con entrego=false, no con un
     * error.
     */
    public EstadoEntregaEstudiante obtenerEstadoPropio(Integer idTarea, Usuario estudiante) {
        Tarea tarea = tareaRepository.findById(idTarea)
                .orElseThrow(() -> new EntregaException(HttpStatus.NOT_FOUND, "tarea-no-encontrada",
                        "Tarea no encontrada", "No existe una tarea con id " + idTarea + "."));

        if (!matriculaRepository.existsByUsuario_IdUsuarioAndMateria_IdMateria(
                estudiante.getIdUsuario(), tarea.getMateria().getIdMateria())) {
            throw new EntregaException(HttpStatus.FORBIDDEN, "no-matriculado",
                    "Acceso prohibido", "No estás matriculado en la materia de esta tarea.");
        }

        return mapAEstado(estudiante, idTarea);
    }

    private EstadoEntregaEstudiante mapAEstado(Usuario estudiante, Integer idTarea) {
        return entregaRepository.findByTarea_IdTareaAndEstudiante_IdUsuario(idTarea, estudiante.getIdUsuario())
                .map(e -> new EstadoEntregaEstudiante(
                        estudiante.getIdUsuario(), estudiante.getNombre(), true,
                        e.getIdEntrega(), e.getNombreArchivo(), e.getFechaEnvio(),
                        e.getCalificacion(), e.getComentarioProf(), e.getFechaCalificacion()))
                .orElseGet(() -> new EstadoEntregaEstudiante(
                        estudiante.getIdUsuario(), estudiante.getNombre(), false,
                        null, null, null, null, null, null));
    }

    /**
     * Valida permiso de descarga y devuelve la entrega si corresponde: el
     * estudiante dueño, o el profesor de la materia de esa tarea. Ningun
     * otro caso (otro estudiante, otro profesor, ADMIN) esta contemplado
     * en el alcance pedido, asi que cae al 403 por defecto.
     */
    public Entrega obtenerParaDescarga(Integer idEntrega, Usuario usuario) {
        Entrega entrega = entregaRepository.findById(idEntrega)
                .orElseThrow(() -> new EntregaException(HttpStatus.NOT_FOUND, "entrega-no-encontrada",
                        "Entrega no encontrada", "No existe una entrega con id " + idEntrega + "."));

        boolean esElEstudianteDueño = entrega.getEstudiante().getIdUsuario().equals(usuario.getIdUsuario());
        Usuario profesorDeLaMateria = entrega.getTarea().getMateria().getProfesor();
        boolean esElProfesorDeLaMateria = profesorDeLaMateria != null
                && profesorDeLaMateria.getIdUsuario().equals(usuario.getIdUsuario());

        if (!esElEstudianteDueño && !esElProfesorDeLaMateria) {
            throw new EntregaException(HttpStatus.FORBIDDEN, "sin-permiso",
                    "Acceso prohibido", "No tienes permiso para descargar esta entrega.");
        }
        return entrega;
    }

    /**
     * Solo el profesor dueño de la materia puede calificar (rol PROFESOR
     * ya lo garantiza @PreAuthorize en el controlador).
     */
    @Auditado
    public Entrega calificar(Integer idEntrega, Usuario profesor, BigDecimal calificacion, String comentarioProf) {
        Entrega entrega = entregaRepository.findById(idEntrega)
                .orElseThrow(() -> new EntregaException(HttpStatus.NOT_FOUND, "entrega-no-encontrada",
                        "Entrega no encontrada", "No existe una entrega con id " + idEntrega + "."));

        validarMateriaPropia(entrega.getTarea().getMateria(), profesor);

        entrega.setCalificacion(calificacion);
        entrega.setComentarioProf(comentarioProf);
        entrega.setFechaCalificacion(LocalDateTime.now());
        return entregaRepository.save(entrega);
    }

    // ---- helpers ----

    private void validarMateriaPropia(Materia materia, Usuario profesor) {
        if (materia.getProfesor() == null || !materia.getProfesor().getIdUsuario().equals(profesor.getIdUsuario())) {
            throw new EntregaException(HttpStatus.FORBIDDEN, "materia-no-propia",
                    "Acceso prohibido", "La materia " + materia.getIdMateria() + " no existe o no la dictas.");
        }
    }

    // Misma logica que segundosHastaLimite() en tareas.html/dashboard.html/
    // profesor.html (badge Vigente/Vencida): combina fecha_entrega +
    // hora_limite en un instante exacto y lo compara contra el momento
    // actual, para que "vencida" signifique lo mismo en el frontend y aca.
    private boolean estaVencida(Tarea tarea) {
        LocalDateTime limite = LocalDateTime.of(tarea.getFechaEntrega(), tarea.getHoraLimite());
        return LocalDateTime.now().isAfter(limite);
    }

    private String obtenerExtension(String nombreArchivo) {
        if (nombreArchivo == null) {
            return "";
        }
        int idx = nombreArchivo.lastIndexOf('.');
        return idx >= 0 && idx < nombreArchivo.length() - 1
                ? nombreArchivo.substring(idx + 1).toUpperCase()
                : "";
    }

    private String guardarArchivoFisico(MultipartFile archivo, Integer idTarea, Integer idEstudiante, String extension) {
        try {
            Path directorio = Paths.get(storagePath);
            Files.createDirectories(directorio);
            String nombreGenerado = "entrega_" + idTarea + "_" + idEstudiante + "_" + System.currentTimeMillis()
                    + "." + extension.toLowerCase();
            Path destino = directorio.resolve(nombreGenerado);
            archivo.transferTo(destino);
            return destino.toString();
        } catch (IOException e) {
            throw new EntregaException(HttpStatus.INTERNAL_SERVER_ERROR, "error-almacenamiento",
                    "Error al guardar el archivo", "No se pudo guardar el archivo en el servidor.");
        }
    }

    private void borrarArchivoFisico(String ruta) {
        try {
            Files.deleteIfExists(Paths.get(ruta));
        } catch (IOException e) {
            // No se relanza: si el archivo viejo no se puede borrar (ya no
            // existe, permisos, etc.) no debe bloquear que la entrega nueva
            // quede guardada -- es un problema de limpieza, no de la subida.
        }
    }
}
