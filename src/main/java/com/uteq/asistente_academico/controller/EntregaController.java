package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.dto.EstadoEntregaEstudiante;
import com.uteq.asistente_academico.entity.Entrega;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.service.EntregaService;
import com.uteq.asistente_academico.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Entregas de tareas: subida de archivo por el estudiante, listado de
 * estado por el profesor, descarga del archivo (estudiante dueño o
 * profesor de la materia), y calificacion por el profesor. Toda la
 * validacion de negocio vive en EntregaService -- este controlador solo
 * resuelve el usuario autenticado y traduce el resultado a HTTP.
 */
@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class EntregaController {

    @Autowired
    private EntregaService entregaService;

    @Autowired
    private UsuarioService usuarioService;

    public record CalificarEntregaRequest(BigDecimal calificacion, String comentarioProf) {
    }

    /**
     * Sube (o reemplaza) la entrega del estudiante autenticado para una
     * tarea. multipart/form-data con un unico campo "archivo".
     */
    @PreAuthorize("hasRole('ESTUDIANTE')")
    @PostMapping(value = "/api/tareas/{idTarea}/entregas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirEntrega(Authentication authentication, HttpServletRequest request,
                                           @PathVariable Integer idTarea,
                                           @RequestParam("archivo") MultipartFile archivo) {
        Optional<Usuario> estudianteOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (estudianteOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        Entrega entrega = entregaService.subir(idTarea, estudianteOpt.get(), archivo);
        return ResponseEntity.ok(entrega);
    }

    /**
     * Estado de entrega de todos los matriculados en la materia de la
     * tarea (entrego/no entrego, fecha, calificacion), para el profesor
     * dueño de esa materia.
     */
    @PreAuthorize("hasRole('PROFESOR')")
    @GetMapping("/api/tareas/{idTarea}/entregas")
    public ResponseEntity<?> listarEntregas(Authentication authentication, HttpServletRequest request,
                                             @PathVariable Integer idTarea) {
        Optional<Usuario> profesorOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (profesorOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        List<EstadoEntregaEstudiante> estado = entregaService.listarPorTarea(idTarea, profesorOpt.get());
        return ResponseEntity.ok(estado);
    }

    /**
     * Descarga el archivo de una entrega. Sin @PreAuthorize por rol fijo:
     * tanto ESTUDIANTE (dueño) como PROFESOR (de la materia) pueden
     * llegar aca, y la distincion la resuelve EntregaService.obtenerParaDescarga.
     */
    @GetMapping("/api/entregas/{idEntrega}/archivo")
    public ResponseEntity<?> descargarArchivo(Authentication authentication, HttpServletRequest request,
                                               @PathVariable Integer idEntrega) {
        Optional<Usuario> usuarioOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        Entrega entrega = entregaService.obtenerParaDescarga(idEntrega, usuarioOpt.get());

        try {
            Path ruta = Paths.get(entrega.getRutaArchivo());
            Resource recurso = new UrlResource(ruta.toUri());
            if (!recurso.exists() || !recurso.isReadable()) {
                return errorArchivoNoDisponible(request, idEntrega);
            }
            ContentDisposition disposicion = ContentDisposition.builder("attachment")
                    .filename(entrega.getNombreArchivo())
                    .build();
            return ResponseEntity.ok()
                    .contentType(mediaTypePorFormato(entrega.getFormato()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                    .body(recurso);
        } catch (MalformedURLException e) {
            return errorArchivoNoDisponible(request, idEntrega);
        }
    }

    /**
     * Registra calificacion + comentario para una entrega. Solo el
     * profesor dueño de la materia.
     */
    @PreAuthorize("hasRole('PROFESOR')")
    @PutMapping("/api/entregas/{idEntrega}/calificacion")
    public ResponseEntity<?> calificarEntrega(Authentication authentication, HttpServletRequest request,
                                               @PathVariable Integer idEntrega,
                                               @RequestBody CalificarEntregaRequest datos) {
        Optional<Usuario> profesorOpt = usuarioService.buscarPorEmail(authentication.getName());
        if (profesorOpt.isEmpty()) {
            return errorUsuarioNoEncontrado(request);
        }
        Entrega entrega = entregaService.calificar(idEntrega, profesorOpt.get(), datos.calificacion(), datos.comentarioProf());
        return ResponseEntity.ok(entrega);
    }

    // ---- helpers ----

    private MediaType mediaTypePorFormato(String formato) {
        return switch (formato) {
            case "PDF" -> MediaType.APPLICATION_PDF;
            case "DOCX" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "XLSX" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private ResponseEntity<ProblemDetail> errorUsuarioNoEncontrado(HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "El usuario del token no existe en el sistema.");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/usuario-no-encontrado"));
        problema.setTitle("Usuario no encontrado");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private ResponseEntity<ProblemDetail> errorArchivoNoDisponible(HttpServletRequest request, Integer idEntrega) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "El archivo de la entrega " + idEntrega + " ya no está disponible en el servidor.");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/archivo-no-disponible"));
        problema.setTitle("Archivo no disponible");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }
}
