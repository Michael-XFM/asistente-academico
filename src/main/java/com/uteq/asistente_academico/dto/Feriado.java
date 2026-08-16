package com.uteq.asistente_academico.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Subconjunto de los campos que devuelve la API publica Nager.Date
 * (https://date.nager.at) para un feriado. Se ignoran el resto de
 * campos de la respuesta (fixed, global, counties, types, ...) porque
 * el asistente academico solo necesita fecha y nombre para avisarle al
 * estudiante que no hay clases ese dia.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Feriado {
    private String date;
    private String localName;
    private String name;
    private String countryCode;
}
