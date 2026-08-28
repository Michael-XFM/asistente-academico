package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.CalificacionRepository;
import com.uteq.asistente_academico.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Boletín de calificaciones (Bloque A.2 — estrategia híbrida de acceso a
 * datos, categoría "reportes"). Siempre resuelve el usuario desde el JWT,
 * igual que TareaController/DashboardController — nunca desde un
 * parámetro enviado por el cliente.
 */
@RestController
@RequestMapping("/api/calificaciones")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class CalificacionController {

    @Autowired
    private CalificacionRepository calificacionRepository;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Lista completa de calificaciones del usuario AUTENTICADO (materia +
     * nota cada una), a diferencia de /reporte que solo da el resumen
     * agregado (promedio, mejor/peor materia). Igual que en reporte(), el
     * id_usuario se resuelve del JWT, nunca de un parametro del cliente.
     *
     * Se aplana a un DTO local {materia, nota} en vez de devolver la
     * entidad Calificacion tal cual: la entidad trae "materia" como el
     * objeto Materia completo (id_materia + nombre), y calificaciones.html
     * ya espera "materia" como el nombre en texto plano.
     */
    @GetMapping
    public ResponseEntity<?> listar(Authentication authentication, HttpServletRequest request) {
        Optional<Usuario> usuarioOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }

        List<CalificacionListado> calificaciones = calificacionRepository
                .findByUsuario_IdUsuario(usuarioOpt.get().getIdUsuario())
                .stream()
                .map(c -> new CalificacionListado(c.getMateria().getNombre(), c.getNota()))
                .toList();
        return ResponseEntity.ok(calificaciones);
    }

    @GetMapping("/reporte")
    public ResponseEntity<?> reporte(Authentication authentication, HttpServletRequest request) {
        Optional<Usuario> usuarioOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }

        // Con parametros OUT, Spring Data devuelve un Map<String,Object>
        // cuyas claves son los nombres declarados en @StoredProcedureParameter.
        // Si el usuario no tiene calificaciones, los cuatro valores llegan
        // en null (ver comentario en sp_reporte_calificaciones.sql).
        Map<String, Object> resultado = calificacionRepository.reporteCalificaciones(usuarioOpt.get().getIdUsuario());
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("promedio", resultado.get("p_promedio"));
        respuesta.put("totalMaterias", resultado.get("p_total_materias"));
        respuesta.put("mejorMateria", resultado.get("p_mejor_materia"));
        respuesta.put("peorMateria", resultado.get("p_peor_materia"));
        return ResponseEntity.ok(respuesta);
    }

    private ResponseEntity<ProblemDetail> errorUsuarioNoEncontrado(HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "El usuario del token no existe en el sistema.");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/usuario-no-encontrado"));
        problema.setTitle("Usuario no encontrado");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private record CalificacionListado(String materia, BigDecimal nota) {
    }
}
