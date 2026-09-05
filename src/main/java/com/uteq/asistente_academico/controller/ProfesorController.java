package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.*;
import com.uteq.asistente_academico.repository.*;
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

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Endpoints de PROFESOR: crear tareas, publicar avisos y registrar
 * calificaciones, todo filtrado a las materias que el profesor dicta
 * (materia.id_profesor) y a los estudiantes matriculados en ellas
 * (Matricula). Todos los metodos requieren @PreAuthorize("hasRole('PROFESOR')").
 *
 * Alcance de "avisos" (decidido explicitamente, no reinterpretar): un
 * profesor puede avisar sobre CUALQUIER tarea de una materia que dicta,
 * sin importar quien la creo (el o el propio estudiante de forma
 * autogestionada) -- no se restringe a "tareas que el profesor creo".
 * El destinatario del aviso SIEMPRE es el dueño de la tarea
 * (tarea.usuario), nunca un campo libre: evita mandar el aviso de la
 * tarea de un estudiante a otro. avisos.id_tarea sigue NOT NULL -- no
 * hay anuncios generales sueltos, a proposito, no se toco ese esquema.
 *
 * Alcance de "crear tareas": una tarea = un estudiante (opcion "a"
 * decidida explicitamente). La asignacion masiva a toda la materia de
 * una sola llamada queda anotada como mejora futura, no implementada.
 */
@RestController
@RequestMapping("/api/profesor")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class ProfesorController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private MatriculaRepository matriculaRepository;

    @Autowired
    private TareaRepository tareaRepository;

    @Autowired
    private AvisoRepository avisoRepository;

    @Autowired
    private CalificacionRepository calificacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public record CrearTareaRequest(Integer idMateria, Integer idUsuario, String titulo, String descripcion, LocalDate fechaEntrega) {
    }

    public record CrearAvisoRequest(Integer idTarea, String mensaje) {
    }

    public record CrearCalificacionRequest(Integer idMateria, Integer idUsuario, BigDecimal nota) {
    }

    /**
     * Materias que dicta el profesor autenticado. Necesario para que el
     * profesor sepa que idMateria puede usar en el resto de los
     * endpoints (no hay forma de adivinarlo desde el frontend sin esto).
     */
    @PreAuthorize("hasRole('PROFESOR')")
    @GetMapping("/materias")
    public ResponseEntity<List<Materia>> misMaterias(Authentication authentication) {
        Usuario profesor = resolverProfesor(authentication);
        return ResponseEntity.ok(materiaRepository.findByProfesor_IdUsuario(profesor.getIdUsuario()));
    }

    /**
     * Estudiantes matriculados en una materia propia. 403 si la materia
     * no es del profesor autenticado (no 404: confirmaria a un profesor
     * que la materia existe, aunque no sea suya).
     */
    @PreAuthorize("hasRole('PROFESOR')")
    @GetMapping("/materias/{idMateria}/estudiantes")
    public ResponseEntity<?> misEstudiantes(Authentication authentication, HttpServletRequest request, @PathVariable Integer idMateria) {
        Usuario profesor = resolverProfesor(authentication);
        Optional<Materia> materiaOpt = validarMateriaPropia(idMateria, profesor);
        if (materiaOpt.isEmpty()) {
            return errorMateriaNoPropia(request, idMateria);
        }
        List<Usuario> estudiantes = matriculaRepository.findByMateria_IdMateria(idMateria).stream()
                .map(Matricula::getUsuario)
                .collect(Collectors.toList());
        return ResponseEntity.ok(estudiantes);
    }

    /**
     * Tareas de una materia propia, para poblar el selector de "sobre
     * cuál tarea avisar" en el formulario de avisos (cada Tarea trae su
     * "usuario" anidado, asi el frontend puede mostrar a que estudiante
     * pertenece sin otra llamada).
     */
    @PreAuthorize("hasRole('PROFESOR')")
    @GetMapping("/materias/{idMateria}/tareas")
    public ResponseEntity<?> misTareasDeMateria(Authentication authentication, HttpServletRequest request, @PathVariable Integer idMateria) {
        Usuario profesor = resolverProfesor(authentication);
        Optional<Materia> materiaOpt = validarMateriaPropia(idMateria, profesor);
        if (materiaOpt.isEmpty()) {
            return errorMateriaNoPropia(request, idMateria);
        }
        return ResponseEntity.ok(tareaRepository.findByMateria_IdMateria(idMateria));
    }

    /**
     * Crea una tarea para UN estudiante matriculado en una materia
     * propia. Genera el codigo de seguimiento igual que
     * TareaController.crear() (mismo sp_generar_codigo_tarea).
     */
    @PreAuthorize("hasRole('PROFESOR')")
    @PostMapping("/tareas")
    public ResponseEntity<?> crearTarea(Authentication authentication, HttpServletRequest request, @RequestBody CrearTareaRequest datos) {
        Usuario profesor = resolverProfesor(authentication);
        Optional<Materia> materiaOpt = validarMateriaPropia(datos.idMateria(), profesor);
        if (materiaOpt.isEmpty()) {
            return errorMateriaNoPropia(request, datos.idMateria());
        }

        Optional<Usuario> estudianteOpt = usuarioRepository.findById(datos.idUsuario());
        if (estudianteOpt.isEmpty()) {
            return errorEstudianteNoEncontrado(request, datos.idUsuario());
        }
        if (!matriculaRepository.existsByUsuario_IdUsuarioAndMateria_IdMateria(datos.idUsuario(), datos.idMateria())) {
            return errorEstudianteNoMatriculado(request, datos.idUsuario(), datos.idMateria());
        }

        Tarea tarea = new Tarea();
        tarea.setMateria(materiaOpt.get());
        tarea.setUsuario(estudianteOpt.get());
        tarea.setTitulo(datos.titulo());
        tarea.setDescripcion(datos.descripcion());
        tarea.setFechaEntrega(datos.fechaEntrega());
        tarea.setCodigo(tareaRepository.spGenerarCodigoTarea());

        return ResponseEntity.ok(tareaRepository.save(tarea));
    }

    /**
     * Publica un aviso sobre una tarea de una materia propia. El
     * destinatario SIEMPRE es tarea.usuario -- no es un campo del
     * request, no se puede mandar el aviso de la tarea de un estudiante
     * a otro (ver nota de alcance en el javadoc de la clase).
     */
    @PreAuthorize("hasRole('PROFESOR')")
    @PostMapping("/avisos")
    public ResponseEntity<?> crearAviso(Authentication authentication, HttpServletRequest request, @RequestBody CrearAvisoRequest datos) {
        Usuario profesor = resolverProfesor(authentication);

        Optional<Tarea> tareaOpt = tareaRepository.findById(datos.idTarea());
        if (tareaOpt.isEmpty()) {
            ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "No existe una tarea con id " + datos.idTarea() + ".");
            problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/tarea-no-encontrada"));
            problema.setTitle("Tarea no encontrada");
            problema.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
        }

        Tarea tarea = tareaOpt.get();
        Integer idMateriaDeLaTarea = tarea.getMateria().getIdMateria();
        if (validarMateriaPropia(idMateriaDeLaTarea, profesor).isEmpty()) {
            return errorMateriaNoPropia(request, idMateriaDeLaTarea);
        }

        Aviso aviso = new Aviso();
        aviso.setTarea(tarea);
        aviso.setUsuario(tarea.getUsuario());
        aviso.setMensaje(datos.mensaje());
        aviso.setLeido(false);
        aviso.setFechaGeneracion(LocalDateTime.now());

        return ResponseEntity.ok(avisoRepository.save(aviso));
    }

    /**
     * Registra una calificacion para un estudiante matriculado en una
     * materia propia.
     */
    @PreAuthorize("hasRole('PROFESOR')")
    @PostMapping("/calificaciones")
    public ResponseEntity<?> crearCalificacion(Authentication authentication, HttpServletRequest request, @RequestBody CrearCalificacionRequest datos) {
        Usuario profesor = resolverProfesor(authentication);
        Optional<Materia> materiaOpt = validarMateriaPropia(datos.idMateria(), profesor);
        if (materiaOpt.isEmpty()) {
            return errorMateriaNoPropia(request, datos.idMateria());
        }

        Optional<Usuario> estudianteOpt = usuarioRepository.findById(datos.idUsuario());
        if (estudianteOpt.isEmpty()) {
            return errorEstudianteNoEncontrado(request, datos.idUsuario());
        }
        if (!matriculaRepository.existsByUsuario_IdUsuarioAndMateria_IdMateria(datos.idUsuario(), datos.idMateria())) {
            return errorEstudianteNoMatriculado(request, datos.idUsuario(), datos.idMateria());
        }

        Calificacion calificacion = new Calificacion();
        calificacion.setUsuario(estudianteOpt.get());
        calificacion.setMateria(materiaOpt.get());
        calificacion.setNota(datos.nota());

        return ResponseEntity.ok(calificacionRepository.save(calificacion));
    }

    // ---- helpers ----

    private Usuario resolverProfesor(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario del token no existe"));
    }

    private Optional<Materia> validarMateriaPropia(Integer idMateria, Usuario profesor) {
        return materiaRepository.findById(idMateria)
                .filter(m -> m.getProfesor() != null && m.getProfesor().getIdUsuario().equals(profesor.getIdUsuario()));
    }

    private ResponseEntity<ProblemDetail> errorMateriaNoPropia(HttpServletRequest request, Integer idMateria) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "La materia " + idMateria + " no existe o no la dictas.");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/materia-no-propia"));
        problema.setTitle("Acceso prohibido");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private ResponseEntity<ProblemDetail> errorEstudianteNoEncontrado(HttpServletRequest request, Integer idUsuario) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "No existe un usuario con id " + idUsuario + ".");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/usuario-no-encontrado"));
        problema.setTitle("Usuario no encontrado");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private ResponseEntity<ProblemDetail> errorEstudianteNoMatriculado(HttpServletRequest request, Integer idUsuario, Integer idMateria) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "El usuario " + idUsuario + " no está matriculado en la materia " + idMateria + ".");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/no-matriculado"));
        problema.setTitle("Acceso prohibido");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }
}
