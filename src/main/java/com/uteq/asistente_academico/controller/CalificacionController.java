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

import java.net.URI;
import java.util.HashMap;
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

    @GetMapping("/reporte")
    public ResponseEntity<?> reporte(Authentication authentication, HttpServletRequest request) {
        Optional<Usuario> usuarioOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "El usuario del token no existe en el sistema.");
            problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/usuario-no-encontrado"));
            problema.setTitle("Usuario no encontrado");
            problema.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
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
}
