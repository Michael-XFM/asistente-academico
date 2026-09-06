package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.audit.Auditado;
import com.uteq.asistente_academico.entity.Tarea;
import com.uteq.asistente_academico.repository.TareaRepository;
import com.uteq.asistente_academico.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Endpoints de administracion, todos restringidos al rol ADMIN via
 * @PreAuthorize. A diferencia de TareaController/DashboardController
 * (donde cada usuario solo ve SUS propios datos, resueltos desde el
 * JWT), aqui el proposito es exactamente lo opuesto: dar visibilidad y
 * gestion sobre los datos de TODOS los usuarios, por lo que el acceso
 * no puede depender de "a quien pertenece el recurso" sino del rol.
 *
 * Se eligieron estos tres endpoints porque son las operaciones tipicas
 * de un panel de administracion (listar usuarios, dar de baja una
 * cuenta, supervisar la actividad de tareas de todo el sistema) y
 * porque exponerlos sin @PreAuthorize seria una escalada de privilegios
 * inmediata: cualquier ESTUDIANTE autenticado podria ver o borrar
 * cuentas ajenas.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class AdminController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TareaRepository tareaRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/usuarios")
    public ResponseEntity<?> listarUsuarios() {
        // Usuario.contrasena tiene @JsonIgnore, asi que el hash nunca
        // sale en esta respuesta aunque se devuelva la entidad completa.
        return ResponseEntity.ok(usuarioRepository.findAll());
    }

    /**
     * OWASP A09 / integridad de datos: un usuario con tareas, avisos,
     * calificaciones o matriculas asociadas NO se borra en cascada
     * silenciosamente. Borrar en cascada perderia registros reales
     * (tareas, notas) sin que nadie lo decida a proposito -- si el admin
     * de verdad quiere dar de baja a alguien con historial, esa es una
     * decision de negocio que hoy no esta implementada (habria que
     * decidir si se anonimiza, se archiva o se borra en cascada
     * explicitamente), no un efecto secundario silencioso de este
     * endpoint. Por eso la FK en la base de datos NO tiene ON DELETE
     * CASCADE, y aca se traduce esa violacion a un 409 explicito en vez
     * de dejar que se escape como 500 generico.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Auditado
    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<?> eliminarUsuario(HttpServletRequest request, @PathVariable Integer id) {
        if (!usuarioRepository.existsById(id)) {
            ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "No existe un usuario con id " + id + ".");
            problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/usuario-no-encontrado"));
            problema.setTitle("Usuario no encontrado");
            problema.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
        }
        try {
            usuarioRepository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "No se puede eliminar el usuario " + id + " porque tiene tareas, calificaciones, " +
                            "avisos o matrículas asociadas. Elimina o reasigna esos registros primero."
            );
            problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/usuario-con-registros-asociados"));
            problema.setTitle("Conflicto: usuario con registros asociados");
            problema.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
        }
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/tareas")
    public ResponseEntity<Page<Tarea>> listarTodasLasTareas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(tareaRepository.findAll(pageable));
    }
}
