package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.UsuarioRepository;
import com.uteq.asistente_academico.service.AuthService;
import com.uteq.asistente_academico.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integracion para /api/chatbot/preguntar. Ninguna de estas
 * llega a llamar a la API real de Gemini: los dos casos cubiertos se
 * resuelven ANTES de esa llamada -- @PreAuthorize corta el 403 antes de
 * que el metodo del controlador se ejecute, y la validacion de mensaje
 * vacio corta el 400 antes de invocar a ChatbotService.responder().
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static final String EMAIL_PROFESOR = "chatbot.profesor@uteq.edu.ec";
    private static final String EMAIL_ESTUDIANTE = "chatbot.estudiante@uteq.edu.ec";
    private static final String CLAVE = "prueba123";

    private String tokenProfesor;
    private String tokenEstudiante;

    @BeforeEach
    void prepararUsuarios() {
        Usuario profesor = usuarioRepository.findByEmail(EMAIL_PROFESOR).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Profesor Chatbot Test");
            u.setEmail(EMAIL_PROFESOR);
            u.setContrasena(CLAVE);
            u.setRol("PROFESOR");
            return usuarioService.registrar(u);
        });

        Usuario estudiante = usuarioRepository.findByEmail(EMAIL_ESTUDIANTE).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Estudiante Chatbot Test");
            u.setEmail(EMAIL_ESTUDIANTE);
            u.setContrasena(CLAVE);
            u.setRol("ESTUDIANTE");
            return usuarioService.registrar(u);
        });

        tokenProfesor = authService.generarToken(profesor);
        tokenEstudiante = authService.generarToken(estudiante);
    }

    @Test
    void profesorIntentaUsarChatbotDevuelve403() throws Exception {
        String body = """
                {"mensaje":"Hola","historial":[]}
                """;

        mockMvc.perform(post("/api/chatbot/preguntar")
                        .header("Authorization", "Bearer " + tokenProfesor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void estudianteMandaMensajeVacioDevuelve400() throws Exception {
        String body = """
                {"mensaje":"","historial":[]}
                """;

        mockMvc.perform(post("/api/chatbot/preguntar")
                        .header("Authorization", "Bearer " + tokenEstudiante)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
