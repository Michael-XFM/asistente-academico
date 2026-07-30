package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.Tarea;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.TareaRepository;
import com.uteq.asistente_academico.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

/**
 * CORREGIDO (OWASP A01 - Control de acceso roto): el controlador
 * original no filtraba por usuario en ningun endpoint. Un usuario
 * autenticado podia ver, editar o borrar las tareas de CUALQUIER otro
 * usuario, solo adivinando el id_tarea. Ahora todas las operaciones se
 * resuelven contra el usuario autenticado (JWT), nunca contra un id
 * enviado por el cliente, siguiendo el mismo patron que
 * DashboardController.
 *
 * Distincion 404 vs 403: si la tarea no existe -> 404. Si existe pero
 * pertenece a otro usuario -> 403 Forbidden (evidencia exigida por el
 * Bloque C.2, control OWASP A01).
 *
 * Bloque A.1: todos los errores responden como ProblemDetail (RFC 7807).
 */
@RestController
@RequestMapping("/api/tareas")
@CrossOrigin(origins = "http://localhost:8080")
public class TareaController {

    @Autowired
    private TareaRepository tareaRepository;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<?> listar(
            Authentication authentication,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Tarea> tareas = tareaRepository.findByUsuario_IdUsuario(usuarioOpt.get().getIdUsuario(), pageable);
        return ResponseEntity.ok(tareas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(Authentication authentication, HttpServletRequest request, @PathVariable Integer id) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        Optional<Tarea> tareaOpt = tareaRepository.findById(id);
        if (tareaOpt.isEmpty()) {
            return errorTareaNoEncontrada(request, id);
        }
        if (!perteneceAlUsuario(tareaOpt.get(), usuarioOpt.get())) {
            return errorSinPermiso(request, "ver");
        }
        return ResponseEntity.ok(tareaOpt.get());
    }

    @PostMapping
    public ResponseEntity<?> crear(Authentication authentication, HttpServletRequest request, @RequestBody Tarea tarea) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        // La tarea SIEMPRE se crea a nombre del usuario autenticado,
        // sin importar que el cliente envie otro id_usuario en el body.
        tarea.setUsuario(usuarioOpt.get());
        return ResponseEntity.ok(tareaRepository.save(tarea));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(Authentication authentication, HttpServletRequest request, @PathVariable Integer id, @RequestBody Tarea tarea) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        Optional<Tarea> tareaOpt = tareaRepository.findById(id);
        if (tareaOpt.isEmpty()) {
            return errorTareaNoEncontrada(request, id);
        }
        if (!perteneceAlUsuario(tareaOpt.get(), usuarioOpt.get())) {
            return errorSinPermiso(request, "editar");
        }
        Tarea t = tareaOpt.get();
        t.setTitulo(tarea.getTitulo());
        t.setDescripcion(tarea.getDescripcion());
        t.setFechaEntrega(tarea.getFechaEntrega());
        return ResponseEntity.ok(tareaRepository.save(t));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(Authentication authentication, HttpServletRequest request, @PathVariable Integer id) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        Optional<Tarea> tareaOpt = tareaRepository.findById(id);
        if (tareaOpt.isEmpty()) {
            return errorTareaNoEncontrada(request, id);
        }
        if (!perteneceAlUsuario(tareaOpt.get(), usuarioOpt.get())) {
            return errorSinPermiso(request, "eliminar");
        }
        tareaRepository.delete(tareaOpt.get());
        return ResponseEntity.ok().build();
    }

    private boolean perteneceAlUsuario(Tarea tarea, Usuario usuario) {
        return tarea.getUsuario() != null
                && tarea.getUsuario().getIdUsuario().equals(usuario.getIdUsuario());
    }

    private Optional<Usuario> resolverUsuarioAutenticado(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName());
    }

    private ResponseEntity<ProblemDetail> errorUsuarioNoEncontrado(HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "El usuario del token no existe en el sistema.");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/usuario-no-encontrado"));
        problema.setTitle("Usuario no encontrado");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private ResponseEntity<ProblemDetail> errorTareaNoEncontrada(HttpServletRequest request, Integer id) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "No existe una tarea con id " + id + ".");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/tarea-no-encontrada"));
        problema.setTitle("Tarea no encontrada");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private ResponseEntity<ProblemDetail> errorSinPermiso(HttpServletRequest request, String accion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "No tienes permiso para " + accion + " esta tarea.");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/sin-permiso"));
        problema.setTitle("Acceso prohibido");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }
}