package com.uteq.asistente_academico.dto;

import java.util.List;

/**
 * Resultado del patron cache-aside en FeriadosService: los feriados
 * mas de donde salieron ("CACHE" o "API_EXTERNA"). El campo origen
 * existe solo para poder demostrar el cache-aside en la defensa oral
 * (llamar dos veces seguidas y ver que la segunda dice "CACHE").
 */
public record ResultadoFeriados(List<Feriado> feriados, String origen) {
}
