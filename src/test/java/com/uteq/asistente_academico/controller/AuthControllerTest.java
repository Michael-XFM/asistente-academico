package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.UsuarioRepository;
import com.uteq.asistente_academico.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integracion para /api/auth. Cada metodo usa un valor
 * distinto de X-Forwarded-For para que el contador de intentos
 * fallidos (LoginRateLimiterService, en memoria por IP) no se
 * comparta entre pruebas y cada una quede aislada.
 *
 * Estas pruebas tambien sirven como evidencia automatizada de los
 * controles OWASP A03 (inyeccion/formato invalido, 422) y A07
 * (bloqueo tras intentos fallidos, 429) ya documentados en
 * docs/mediciones/sec/.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static final String EMAIL = "auth.test@uteq.edu.ec";
    private static final String CLAVE = "prueba123";

    @BeforeEach
    void prepararUsuario() {
        usuarioRepository.findByEmail(EMAIL).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Usuario Auth Test");
            u.setEmail(EMAIL);
            u.setContrasena(CLAVE);
            u.setRol("estudiante");
            return usuarioService.registrar(u);
        });
    }

    @Test
    void loginConCredencialesValidasDevuelve200YToken() throws Exception {
        String body = """
                {"email":"%s","contrasena":"%s"}
                """.formatted(EMAIL, CLAVE);

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.rol").value("estudiante"));
    }

    // OWASP A03 (inyeccion): un payload tipo ' OR '1'='1 no matchea el
    // patron de email y se rechaza con 422 antes de tocar la base de
    // datos, sin importar el contenido del campo.
    @Test
    void loginConPayloadDeInyeccionEnEmailDevuelve422() throws Exception {
        String body = """
                {"email":"' OR '1'='1","contrasena":"cualquiera"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Formato de entrada invalido"));
    }

    @Test
    void loginConContrasenaIncorrectaDevuelve400() throws Exception {
        String body = """
                {"email":"%s","contrasena":"claveIncorrecta"}
                """.formatted(EMAIL);

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.0.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Credenciales invalidas"));
    }

    // OWASP A07: al sexto intento fallido consecutivo desde la misma
    // IP, el sistema responde 429 en vez de seguir aceptando intentos.
    @Test
    void seisIntentosFallidosConsecutivosDevuelve429EnElSexto() throws Exception {
        String ip = "10.0.0.4";
        String bodyIncorrecto = """
                {"email":"%s","contrasena":"claveIncorrecta"}
                """.formatted(EMAIL);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyIncorrecto))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyIncorrecto))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Limite de intentos excedido"));
    }

    @Test
    void logoutSinTokenDevuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutConTokenValidoDevuelve200() throws Exception {
        String bodyLogin = """
                {"email":"%s","contrasena":"%s"}
                """.formatted(EMAIL, CLAVE);

        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.0.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLogin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = respuesta.split("\"token\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}