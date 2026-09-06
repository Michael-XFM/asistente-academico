package com.uteq.asistente_academico.dto;

/**
 * Un turno del historial de conversacion del chatbot, tal como lo arma y
 * reenvia el frontend en cada llamada (no se persiste en el backend).
 * rol: "usuario" o "asistente" -- ChatbotService lo traduce a los roles
 * "user"/"model" que espera la API de Gemini.
 */
public record ChatMensaje(String rol, String contenido) {
}
