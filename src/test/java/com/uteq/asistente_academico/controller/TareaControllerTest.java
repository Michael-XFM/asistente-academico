package com.uteq.asistente_academico.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uteq.asistente_academico.entity.Materia;
import com.uteq.asistente_academico.entity.Tarea;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.MateriaRepository;
import com.uteq.asistente_academico.repository.TareaRepository;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integracion para /api/tareas.
 *
 * Corregido tras encontrar (leyendo el codigo, no solo probando "el
 * camino feliz") que el controlador original no filtraba por usuario:
 * cualquiera podia ver/editar/borrar tareas de otro usuario (OWASP A01
 * - control de acceso roto). Estas pruebas verifican explicitamente
 * ese aislamiento: 404 cuando la tarea no existe, 403 cuando existe
 * pero pertenece a otro usuario (evidencia exigida por el Bloque C.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TareaControllerTest {

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

    private static final String EMAIL_A = "tarea.usuarioA@uteq.edu.ec";
    private static final String EMAIL_B = "tarea.usuarioB@uteq.edu.ec";
    private static final String CLAVE = "prueba123";

    private String tokenA;
    private String tokenB;
    private Materia materia;
    private Tarea tareaDeA;

    @BeforeEach
    void prepararDatos() {
        Usuario usuarioA = usuarioRepository.findByEmail(EMAIL_A).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Usuario A");
            u.setEmail(EMAIL_A);
            u.setContrasena(CLAVE);
            u.setRol("estudiante");
            return usuarioService.registrar(u);
        });

        Usuario usuarioB = usuarioRepository.findByEmail(EMAIL_B).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Usuario B");
            u.setEmail(EMAIL_B);
            u.setContrasena(CLAVE);
            u.setRol("estudiante");
            return usuarioService.registrar(u);
        });

        materia = new Materia();
        materia.setNombre("Materia Tarea Test");
        materia = materiaRepository.save(materia);

        // Tarea nueva de A en cada corrida (para no depender de estado previo)
        tareaDeA = new Tarea();
        tareaDeA.setUsuario(usuarioA);
        tareaDeA.setMateria(materia);
        tareaDeA.setTitulo("Tarea de A");
        tareaDeA.setDescripcion("Pertenece al usuario A");
        tareaDeA.setFechaEntrega(LocalDate.now().plusDays(3));
        tareaDeA = tareaRepository.save(tareaDeA);

        tokenA = authService.generarToken(usuarioA);
        tokenB = authService.generarToken(usuarioB);
    }

    @Test
    void listarSinTokenDevuelve403() throws Exception {
        mockMvc.perform(get("/api/tareas"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearTareaQuedaAsignadaAlUsuarioAutenticado() throws Exception {
        String body = """
                {"titulo":"Nueva tarea","descripcion":"desc","fechaEntrega":"2026-12-01",
                 "materia":{"idMateria":%d}}
                """.formatted(materia.getIdMateria());

        mockMvc.perform(post("/api/tareas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(EMAIL_A));
    }

    @Test
    void listarSoloDevuelveTareasDelUsuarioAutenticado() throws Exception {
        mockMvc.perform(get("/api/tareas").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.usuario.email == '" + EMAIL_B + "')]").isEmpty());
    }

    @Test
    void tareaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/tareas/999999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioBNoPuedeVerTareaDeUsuarioADevuelve403() throws Exception {
        mockMvc.perform(get("/api/tareas/" + tareaDeA.getIdTarea())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioBNoPuedeEditarTareaDeUsuarioADevuelve403() throws Exception {
        String body = """
                {"titulo":"Hackeada","descripcion":"intento no autorizado","fechaEntrega":"2026-01-01"}
                """;

        mockMvc.perform(put("/api/tareas/" + tareaDeA.getIdTarea())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        // Confirmamos que el titulo original de A no cambio
        mockMvc.perform(get("/api/tareas/" + tareaDeA.getIdTarea())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.titulo").value("Tarea de A"));
    }

    @Test
    void usuarioBNoPuedeEliminarTareaDeUsuarioADevuelve403() throws Exception {
        mockMvc.perform(delete("/api/tareas/" + tareaDeA.getIdTarea())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // La tarea de A sigue existiendo
        mockMvc.perform(get("/api/tareas/" + tareaDeA.getIdTarea())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioAPuedeEditarSuPropiaTarea() throws Exception {
        String body = """
                {"titulo":"Titulo actualizado","descripcion":"desc actualizada","fechaEntrega":"2026-12-15"}
                """;

        mockMvc.perform(put("/api/tareas/" + tareaDeA.getIdTarea())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Titulo actualizado"));
    }
}