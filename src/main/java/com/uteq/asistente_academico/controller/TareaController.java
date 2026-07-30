package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.Tarea;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.TareaRepository;
import com.uteq.asistente_academico.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Page<Tarea>> listar(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Tarea> tareas = tareaRepository.findByUsuario_IdUsuario(usuarioOpt.get().getIdUsuario(), pageable);
        return ResponseEntity.ok(tareas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(Authentication authentication, @PathVariable Integer id) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        Optional<Tarea> tareaOpt = tareaRepository.findById(id);
        if (tareaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!perteneceAlUsuario(tareaOpt.get(), usuarioOpt.get())) {
            return ResponseEntity.status(403).body("No tienes permiso para ver esta tarea");
        }
        return ResponseEntity.ok(tareaOpt.get());
    }

    @PostMapping
    public ResponseEntity<Tarea> crear(Authentication authentication, @RequestBody Tarea tarea) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        // La tarea SIEMPRE se crea a nombre del usuario autenticado,
        // sin importar que el cliente envie otro id_usuario en el body.
        tarea.setUsuario(usuarioOpt.get());
        return ResponseEntity.ok(tareaRepository.save(tarea));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(Authentication authentication, @PathVariable Integer id, @RequestBody Tarea tarea) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        Optional<Tarea> tareaOpt = tareaRepository.findById(id);
        if (tareaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!perteneceAlUsuario(tareaOpt.get(), usuarioOpt.get())) {
            return ResponseEntity.status(403).body("No tienes permiso para editar esta tarea");
        }
        Tarea t = tareaOpt.get();
        t.setTitulo(tarea.getTitulo());
        t.setDescripcion(tarea.getDescripcion());
        t.setFechaEntrega(tarea.getFechaEntrega());
        return ResponseEntity.ok(tareaRepository.save(t));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(Authentication authentication, @PathVariable Integer id) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        Optional<Tarea> tareaOpt = tareaRepository.findById(id);
        if (tareaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!perteneceAlUsuario(tareaOpt.get(), usuarioOpt.get())) {
            return ResponseEntity.status(403).body("No tienes permiso para eliminar esta tarea");
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
}