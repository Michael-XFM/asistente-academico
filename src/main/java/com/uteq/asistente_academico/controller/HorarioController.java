package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.Horario;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.HorarioRepository;
import com.uteq.asistente_academico.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Horarios: listado propio (vía matrícula) y validación cruzada de
 * disponibilidad de aula (Bloque A.2, categoría "validaciones cruzadas").
 * No incluye CRUD completo de Horario porque el frontend no lo gestiona
 * (los horarios los carga un administrador directo en base de datos).
 */
@RestController
@RequestMapping("/api/horarios")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class HorarioController {

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    public record ValidarDisponibilidadRequest(String diaSemana, LocalTime horaInicio, LocalTime horaFin, String aula) {
    }

    /**
     * Desbloquea horarios.html: un estudiante solo ve horario de las
     * materias donde tiene una fila en Matricula. Usuario siempre
     * resuelto desde el JWT, nunca de un parámetro del cliente (mismo
     * criterio que TareaController/DashboardController).
     */
    @GetMapping("/mios")
    public ResponseEntity<?> misHorarios(Authentication authentication) {
        Optional<Usuario> usuarioOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Usuario no encontrado");
        }
        List<Horario> horarios = horarioRepository.findMisHorarios(usuarioOpt.get().getIdUsuario());
        return ResponseEntity.ok(horarios);
    }

    @PostMapping("/validar-disponibilidad")
    public ResponseEntity<?> validarDisponibilidad(@RequestBody ValidarDisponibilidadRequest datos) {
        Boolean disponible = horarioRepository.spValidarDisponibilidadHorario(
                datos.diaSemana(), datos.horaInicio(), datos.horaFin(), datos.aula());
        return ResponseEntity.ok(Map.of(
                "disponible", disponible,
                "diaSemana", datos.diaSemana(),
                "aula", datos.aula()
        ));
    }
}
