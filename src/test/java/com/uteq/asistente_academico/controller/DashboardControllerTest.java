package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.*;
import com.uteq.asistente_academico.repository.*;
import com.uteq.asistente_academico.service.AuthService;
import com.uteq.asistente_academico.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integracion para el endpoint /api/dashboard, agregado en el
 * Bloque A.2 (estrategia de acceso a datos ORM vs procedimientos
 * almacenados). No existian pruebas automatizadas para este controlador
 * hasta ahora; solo se habia verificado manualmente en navegador/Postman.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private TareaRepository tareaRepository;

    @Autowired
    private AvisoRepository avisoRepository;

    @Autowired
    private CalificacionRepository calificacionRepository;

    private static final String EMAIL_PRUEBA = "dashboard.test@uteq.edu.ec";
    private static final String CLAVE_PRUEBA = "prueba123";

    private String token;

    /**
     * Crea (de forma idempotente para el usuario, pero SIEMPRE fresca
     * para la tarea/aviso/calificacion) los datos de prueba necesarios.
     *
     * Importante: el aviso se crea de nuevo en CADA corrida de CADA
     * prueba (no se reutiliza uno viejo), porque la prueba de "marcar
     * avisos leidos" modifica su estado (leido = true). Si se
     * reutilizara el mismo aviso entre corridas, una corrida posterior
     * ya lo encontraria leido y la asercion de "al menos 1 aviso no
     * leido" fallaria — eso fue exactamente lo que paso la primera vez
     * que se corrio la suite dos veces seguidas.
     */
    @BeforeEach
    void prepararDatosDePrueba() {
        Usuario usuario = usuarioRepository.findByEmail(EMAIL_PRUEBA).orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setNombre("Estudiante Dashboard Test");
            nuevo.setEmail(EMAIL_PRUEBA);
            nuevo.setContrasena(CLAVE_PRUEBA);
            nuevo.setRol("estudiante");
            return usuarioService.registrar(nuevo);
        });

        Materia materia = new Materia();
        materia.setNombre("Materia de Prueba Dashboard");
        materia = materiaRepository.save(materia);

        Tarea tarea = new Tarea();
        tarea.setUsuario(usuario);
        tarea.setMateria(materia);
        tarea.setTitulo("Tarea de prueba");
        tarea.setDescripcion("Generada por DashboardControllerTest");
        tarea.setFechaEntrega(LocalDate.now().plusDays(5));
        tarea = tareaRepository.save(tarea);

        Aviso aviso = new Aviso();
        aviso.setUsuario(usuario);
        aviso.setTarea(tarea);
        aviso.setMensaje("Aviso de prueba");
        aviso.setLeido(false);
        aviso.setFechaGeneracion(LocalDateTime.now());
        avisoRepository.save(aviso);

        Calificacion calificacion = new Calificacion();
        calificacion.setUsuario(usuario);
        calificacion.setMateria(materia);
        calificacion.setNota(new BigDecimal("8.50"));
        calificacionRepository.save(calificacion);

        token = authService.generarToken(usuario);
    }

    @Test
    void dashboardSinTokenDevuelve403() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardConTokenValidoDevuelveResumen() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen").exists())
                .andExpect(jsonPath("$.resumen.tareasPendientes").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.resumen.avisosNoLeidos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.tareasPendientes").isArray());
    }

    @Test
    void marcarAvisosLeidosSinTokenDevuelve403() throws Exception {
        mockMvc.perform(put("/api/dashboard/avisos/marcar-leidos"))
                .andExpect(status().isForbidden());
    }

    @Test
    void marcarAvisosLeidosConTokenValidoActualizaAvisos() throws Exception {
        // Primero confirmamos que hay al menos un aviso no leido
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.resumen.avisosNoLeidos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Marcamos como leidos
        mockMvc.perform(put("/api/dashboard/avisos/marcar-leidos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisosMarcados").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Verificamos que ya no queden avisos no leidos para este usuario
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.resumen.avisosNoLeidos").value(0));
    }
}