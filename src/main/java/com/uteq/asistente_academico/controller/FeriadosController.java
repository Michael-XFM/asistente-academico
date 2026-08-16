package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.dto.ResultadoFeriados;
import com.uteq.asistente_academico.service.FeriadosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.HashMap;
import java.util.Map;

/**
 * Feriados nacionales, consumidos de una API externa (Nager.Date) con
 * cache-aside en Redis (ver FeriadosService). Relevante para el
 * asistente academico porque un feriado significa "no hay clases ese
 * dia" al calcular fechas de entrega y horarios.
 *
 * El campo "origen" en la respuesta ("CACHE" o "API_EXTERNA") es solo
 * para poder demostrar el cache-aside en vivo: la primera llamada para
 * un año/país da API_EXTERNA, las siguientes (dentro del TTL) dan
 * CACHE.
 */
@RestController
@RequestMapping("/api/feriados")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class FeriadosController {

    @Autowired
    private FeriadosService feriadosService;

    @GetMapping
    public ResponseEntity<?> obtenerFeriados(
            @RequestParam(required = false) Integer anio,
            @RequestParam(defaultValue = "EC") String pais) {
        int anioConsulta = anio != null ? anio : Year.now().getValue();
        String paisConsulta = pais.toUpperCase();

        ResultadoFeriados resultado = feriadosService.obtener(anioConsulta, paisConsulta);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("anio", anioConsulta);
        respuesta.put("pais", paisConsulta);
        respuesta.put("origen", resultado.origen());
        respuesta.put("cantidad", resultado.feriados().size());
        respuesta.put("feriados", resultado.feriados());
        return ResponseEntity.ok(respuesta);
    }
}
