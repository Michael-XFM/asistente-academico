package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.Materia;
import com.uteq.asistente_academico.repository.MateriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catálogo de materias. A diferencia de TareaController/CalificacionController
 * no resuelve el usuario desde el JWT: la lista es global, igual para
 * cualquier estudiante autenticado, no depende de quién la pida. Sigue
 * exigiendo autenticación por la regla general anyRequest().authenticated()
 * de SecurityConfig — no queda pública.
 */
@RestController
@RequestMapping("/api/materias")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class MateriaController {

    @Autowired
    private MateriaRepository materiaRepository;

    @GetMapping
    public ResponseEntity<List<Materia>> listar() {
        return ResponseEntity.ok(materiaRepository.findAll());
    }
}
