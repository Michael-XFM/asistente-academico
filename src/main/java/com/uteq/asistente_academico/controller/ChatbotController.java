package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.dto.ChatMensaje;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.service.ChatbotService;
import com.uteq.asistente_academico.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Chatbot academico: un unico endpoint que recibe el mensaje nuevo del
 * estudiante + el historial de la conversacion (armado y reenviado por
 * el frontend, nunca persistido aca) y devuelve la respuesta generada
 * por ChatbotService/Gemini. Rol ESTUDIANTE unicamente -- el contexto
 * que arma el servicio (tareas, horario) es siempre el del propio
 * estudiante autenticado, nunca de otro usuario.
 */
@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class ChatbotController {

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private UsuarioService usuarioService;

    public record PreguntarRequest(String mensaje, List<ChatMensaje> historial) {
    }

    public record PreguntarResponse(String respuesta) {
    }

    @PreAuthorize("hasRole('ESTUDIANTE')")
    @PostMapping("/preguntar")
    public ResponseEntity<?> preguntar(Authentication authentication, HttpServletRequest request,
                                        @RequestBody PreguntarRequest datos) {
        Optional<Usuario> estudianteOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (estudianteOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        if (datos.mensaje() == null || datos.mensaje().isBlank()) {
            return errorMensajeVacio(request);
        }
        String respuesta = chatbotService.responder(estudianteOpt.get(), datos.mensaje(), datos.historial());
        return ResponseEntity.ok(new PreguntarResponse(respuesta));
    }

    private ResponseEntity<ProblemDetail> errorMensajeVacio(HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Escribí una consulta antes de enviar.");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/mensaje-vacio"));
        problema.setTitle("Solicitud inválida");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private ResponseEntity<ProblemDetail> errorUsuarioNoEncontrado(HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "El usuario del token no existe en el sistema.");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/usuario-no-encontrado"));
        problema.setTitle("Usuario no encontrado");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }
}
