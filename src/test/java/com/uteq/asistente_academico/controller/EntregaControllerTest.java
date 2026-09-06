package com.uteq.asistente_academico.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uteq.asistente_academico.entity.Entrega;
import com.uteq.asistente_academico.entity.Materia;
import com.uteq.asistente_academico.entity.Matricula;
import com.uteq.asistente_academico.entity.Tarea;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.EntregaRepository;
import com.uteq.asistente_academico.repository.MateriaRepository;
import com.uteq.asistente_academico.repository.MatriculaRepository;
import com.uteq.asistente_academico.repository.TareaRepository;
import com.uteq.asistente_academico.repository.UsuarioRepository;
import com.uteq.asistente_academico.service.AuthService;
import com.uteq.asistente_academico.service.UsuarioService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integracion para el flujo de entregas (subida, listado del
 * profesor, descarga, calificacion). Mismo estilo que TareaControllerTest:
 * @SpringBootTest + @AutoConfigureMockMvc contra la base real (Flyway),
 * tokens generados directo con AuthService (sin pasar por /api/auth/login),
 * usuarios idempotentes en @BeforeEach pero materia/tarea/matricula
 * siempre frescas por corrida.
 *
 * IMPORTANTE (a diferencia de TareaControllerTest/DashboardControllerTest/
 * AuthControllerTest, que usan setRol("estudiante") en minuscula sin que
 * les importe): EntregaController protege sus endpoints con
 * @PreAuthorize("hasRole(...)"), que compara contra la autoridad
 * ROLE_ESTUDIANTE/ROLE_PROFESOR armada por JwtAuthFilter a partir del
 * claim "rol" -- esa comparacion es case-sensitive, asi que aca los
 * usuarios de prueba se crean con setRol("ESTUDIANTE")/setRol("PROFESOR")
 * en mayuscula. Usar minuscula aca haria que todo el rol-gating devuelva
 * 403 sin importar que tan bien este el resto de la logica.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntregaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private MatriculaRepository matriculaRepository;

    @Autowired
    private TareaRepository tareaRepository;

    @Autowired
    private EntregaRepository entregaRepository;

    private static final String EMAIL_PROFESOR_DUENO = "entrega.profesor.dueno@uteq.edu.ec";
    private static final String EMAIL_PROFESOR_AJENO = "entrega.profesor.ajeno@uteq.edu.ec";
    private static final String EMAIL_ESTUDIANTE_MATRICULADO = "entrega.estudiante.matriculado@uteq.edu.ec";
    private static final String EMAIL_ESTUDIANTE_NO_MATRICULADO = "entrega.estudiante.no.matriculado@uteq.edu.ec";
    private static final String CLAVE = "prueba123";

    private String tokenProfesorDueno;
    private String tokenProfesorAjeno;
    private String tokenEstudianteMatriculado;
    private String tokenEstudianteNoMatriculado;

    private Tarea tareaVigente;
    private Tarea tareaVencida;

    @BeforeEach
    void prepararDatos() {
        Usuario profesorDueno = usuarioRepository.findByEmail(EMAIL_PROFESOR_DUENO).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Profesor Dueno");
            u.setEmail(EMAIL_PROFESOR_DUENO);
            u.setContrasena(CLAVE);
            u.setRol("PROFESOR");
            return usuarioService.registrar(u);
        });

        Usuario profesorAjeno = usuarioRepository.findByEmail(EMAIL_PROFESOR_AJENO).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Profesor Ajeno");
            u.setEmail(EMAIL_PROFESOR_AJENO);
            u.setContrasena(CLAVE);
            u.setRol("PROFESOR");
            return usuarioService.registrar(u);
        });

        Usuario estudianteMatriculado = usuarioRepository.findByEmail(EMAIL_ESTUDIANTE_MATRICULADO).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Estudiante Matriculado");
            u.setEmail(EMAIL_ESTUDIANTE_MATRICULADO);
            u.setContrasena(CLAVE);
            u.setRol("ESTUDIANTE");
            return usuarioService.registrar(u);
        });

        Usuario estudianteNoMatriculado = usuarioRepository.findByEmail(EMAIL_ESTUDIANTE_NO_MATRICULADO).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Estudiante No Matriculado");
            u.setEmail(EMAIL_ESTUDIANTE_NO_MATRICULADO);
            u.setContrasena(CLAVE);
            u.setRol("ESTUDIANTE");
            return usuarioService.registrar(u);
        });

        // Materia fresca en cada corrida: evita choques con la restriccion
        // UNIQUE (id_usuario, id_materia) de Matricula si se reutilizara
        // una materia vieja con una matricula ya existente.
        Materia materia = new Materia();
        materia.setNombre("Materia Entrega Test");
        materia.setProfesor(profesorDueno);
        materia = materiaRepository.save(materia);

        // fechaMatricula NO tiene default en el lado Java (a diferencia de
        // Tarea.horaLimite) -- si se omite, Hibernate manda NULL explicito
        // en el INSERT y choca con el NOT NULL de la columna. Hay que
        // fijarla a mano.
        Matricula matricula = new Matricula();
        matricula.setUsuario(estudianteMatriculado);
        matricula.setMateria(materia);
        matricula.setFechaMatricula(LocalDateTime.now());
        matriculaRepository.save(matricula);

        tareaVigente = new Tarea();
        tareaVigente.setUsuario(estudianteMatriculado);
        tareaVigente.setMateria(materia);
        tareaVigente.setTitulo("Tarea vigente de entrega test");
        tareaVigente.setDescripcion("Generada por EntregaControllerTest");
        tareaVigente.setFechaEntrega(LocalDate.now().plusDays(5));
        tareaVigente = tareaRepository.save(tareaVigente);

        tareaVencida = new Tarea();
        tareaVencida.setUsuario(estudianteMatriculado);
        tareaVencida.setMateria(materia);
        tareaVencida.setTitulo("Tarea vencida de entrega test");
        tareaVencida.setDescripcion("Generada por EntregaControllerTest");
        tareaVencida.setFechaEntrega(LocalDate.now().minusDays(1));
        tareaVencida = tareaRepository.save(tareaVencida);

        tokenProfesorDueno = authService.generarToken(profesorDueno);
        tokenProfesorAjeno = authService.generarToken(profesorAjeno);
        tokenEstudianteMatriculado = authService.generarToken(estudianteMatriculado);
        tokenEstudianteNoMatriculado = authService.generarToken(estudianteNoMatriculado);
    }

    private MockMultipartFile archivoValido() {
        return new MockMultipartFile("archivo", "trabajo.pdf", "application/pdf",
                "contenido de prueba".getBytes(StandardCharsets.UTF_8));
    }

    private Integer subirEntregaValidaYObtenerId(String token, Integer idTarea) throws Exception {
        String respuesta = mockMvc.perform(multipart("/api/tareas/" + idTarea + "/entregas")
                        .file(archivoValido())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("idEntrega").asInt();
    }

    // ---------- Subida ----------

    @Test
    void estudianteMatriculadoSubeArchivoValidoDentroDePlazoDevuelve200() throws Exception {
        mockMvc.perform(multipart("/api/tareas/" + tareaVigente.getIdTarea() + "/entregas")
                        .file(archivoValido())
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreArchivo").value("trabajo.pdf"))
                .andExpect(jsonPath("$.formato").value("PDF"));
    }

    @Test
    void mismoEstudianteVuelveASubirReemplazaLaMismaEntrega() throws Exception {
        Integer idPrimeraEntrega = subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        MockMultipartFile segundoArchivo = new MockMultipartFile("archivo", "trabajo_v2.pdf", "application/pdf",
                "contenido version 2".getBytes(StandardCharsets.UTF_8));
        String respuesta = mockMvc.perform(multipart("/api/tareas/" + tareaVigente.getIdTarea() + "/entregas")
                        .file(segundoArchivo)
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreArchivo").value("trabajo_v2.pdf"))
                .andReturn().getResponse().getContentAsString();
        Integer idSegundaEntrega = objectMapper.readTree(respuesta).get("idEntrega").asInt();

        Assertions.assertEquals(idPrimeraEntrega, idSegundaEntrega,
                "Reemplazar una entrega debe actualizar la misma fila, no crear una nueva");
    }

    @Test
    void estudianteNoMatriculadoIntentaSubirDevuelve403() throws Exception {
        mockMvc.perform(multipart("/api/tareas/" + tareaVigente.getIdTarea() + "/entregas")
                        .file(archivoValido())
                        .header("Authorization", "Bearer " + tokenEstudianteNoMatriculado))
                .andExpect(status().isForbidden());
    }

    @Test
    void formatoNoPermitidoDevuelve400() throws Exception {
        MockMultipartFile archivoTxt = new MockMultipartFile("archivo", "trabajo.txt", "text/plain",
                "esto no es un pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/tareas/" + tareaVigente.getIdTarea() + "/entregas")
                        .file(archivoTxt)
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archivoMasDe10MBDevuelve400() throws Exception {
        byte[] contenidoGrande = new byte[11 * 1024 * 1024];
        MockMultipartFile archivoGrande = new MockMultipartFile("archivo", "grande.pdf", "application/pdf", contenidoGrande);

        mockMvc.perform(multipart("/api/tareas/" + tareaVigente.getIdTarea() + "/entregas")
                        .file(archivoGrande)
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subirATareaVencidaDevuelve409() throws Exception {
        mockMvc.perform(multipart("/api/tareas/" + tareaVencida.getIdTarea() + "/entregas")
                        .file(archivoValido())
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isConflict());
    }

    @Test
    void subirATareaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(multipart("/api/tareas/999999/entregas")
                        .file(archivoValido())
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isNotFound());
    }

    // ---------- Listado ----------

    @Test
    void profesorDuenoListaDevuelve200ConEstadoDeMatriculados() throws Exception {
        subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        mockMvc.perform(get("/api/tareas/" + tareaVigente.getIdTarea() + "/entregas")
                        .header("Authorization", "Bearer " + tokenProfesorDueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreEstudiante").value("Estudiante Matriculado"))
                .andExpect(jsonPath("$[0].entrego").value(true))
                .andExpect(jsonPath("$[0].nombreArchivo").value("trabajo.pdf"));
    }

    @Test
    void profesorQueNoDictaLaMateriaIntentaListarDevuelve403() throws Exception {
        mockMvc.perform(get("/api/tareas/" + tareaVigente.getIdTarea() + "/entregas")
                        .header("Authorization", "Bearer " + tokenProfesorAjeno))
                .andExpect(status().isForbidden());
    }

    @Test
    void estudianteIntentaAccederAlListadoDelProfesorDevuelve403() throws Exception {
        mockMvc.perform(get("/api/tareas/" + tareaVigente.getIdTarea() + "/entregas")
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isForbidden());
    }

    // ---------- Descarga ----------

    @Test
    void estudianteDuenoDescargaSuPropiaEntregaDevuelve200() throws Exception {
        Integer idEntrega = subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        mockMvc.perform(get("/api/entregas/" + idEntrega + "/archivo")
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("trabajo.pdf")));
    }

    @Test
    void profesorDeLaMateriaDescargaDevuelve200() throws Exception {
        Integer idEntrega = subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        mockMvc.perform(get("/api/entregas/" + idEntrega + "/archivo")
                        .header("Authorization", "Bearer " + tokenProfesorDueno))
                .andExpect(status().isOk());
    }

    @Test
    void otroEstudianteIntentaDescargarDevuelve403() throws Exception {
        Integer idEntrega = subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        mockMvc.perform(get("/api/entregas/" + idEntrega + "/archivo")
                        .header("Authorization", "Bearer " + tokenEstudianteNoMatriculado))
                .andExpect(status().isForbidden());
    }

    @Test
    void otroProfesorIntentaDescargarDevuelve403() throws Exception {
        Integer idEntrega = subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        mockMvc.perform(get("/api/entregas/" + idEntrega + "/archivo")
                        .header("Authorization", "Bearer " + tokenProfesorAjeno))
                .andExpect(status().isForbidden());
    }

    @Test
    void descargarEntregaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/entregas/999999/archivo")
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado))
                .andExpect(status().isNotFound());
    }

    // ---------- Calificacion ----------

    @Test
    void profesorDuenoCalificaDevuelve200YGuardaCalificacion() throws Exception {
        Integer idEntrega = subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        String body = """
                {"calificacion": 8.75, "comentarioProf": "Buen trabajo, falta justificar el diseño."}
                """;

        mockMvc.perform(put("/api/entregas/" + idEntrega + "/calificacion")
                        .header("Authorization", "Bearer " + tokenProfesorDueno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacion").value(8.75))
                .andExpect(jsonPath("$.comentarioProf").value("Buen trabajo, falta justificar el diseño."))
                .andExpect(jsonPath("$.fechaCalificacion").exists());
    }

    @Test
    void estudianteIntentaCalificarDevuelve403() throws Exception {
        Integer idEntrega = subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        String body = """
                {"calificacion": 10, "comentarioProf": "intento no autorizado"}
                """;

        mockMvc.perform(put("/api/entregas/" + idEntrega + "/calificacion")
                        .header("Authorization", "Bearer " + tokenEstudianteMatriculado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void profesorAjenoIntentaCalificarDevuelve403YNoMuta() throws Exception {
        Integer idEntrega = subirEntregaValidaYObtenerId(tokenEstudianteMatriculado, tareaVigente.getIdTarea());

        String body = """
                {"calificacion": 1.0, "comentarioProf": "intento no autorizado"}
                """;

        mockMvc.perform(put("/api/entregas/" + idEntrega + "/calificacion")
                        .header("Authorization", "Bearer " + tokenProfesorAjeno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        Entrega entrega = entregaRepository.findById(idEntrega).orElseThrow();
        Assertions.assertNull(entrega.getCalificacion(),
                "El intento de calificar de un profesor ajeno no debe mutar la entrega");
    }
}
