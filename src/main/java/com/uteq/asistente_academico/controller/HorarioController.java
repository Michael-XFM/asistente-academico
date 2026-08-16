package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.repository.HorarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.Map;

/**
 * Validación de horarios (Bloque A.2 — estrategia híbrida de acceso a
 * datos, categoría "validaciones cruzadas"). No incluye CRUD completo de
 * Horario porque el frontend actual no gestiona horarios: este endpoint
 * existe puntualmente para poder invocar y demostrar
 * fn_validar_disponibilidad_horario desde una ruta HTTP real.
 */
@RestController
@RequestMapping("/api/horarios")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class HorarioController {

    @Autowired
    private HorarioRepository horarioRepository;

    public record ValidarDisponibilidadRequest(String diaSemana, LocalTime horaInicio, LocalTime horaFin, String aula) {
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
