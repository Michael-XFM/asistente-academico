package com.uteq.asistente_academico.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Archivo que un estudiante sube para una tarea (V5__crear_tabla_entrega.sql).
 * No hay restriccion a nivel de base de datos que obligue a que
 * "estudiante" tenga rol ESTUDIANTE (usuarios.rol es VARCHAR libre, mismo
 * criterio que Matricula) -- se valida en EntregaService.
 */
@Data
@Entity
@Table(name = "entrega")
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entrega")
    private Integer idEntrega;

    @ManyToOne
    @JoinColumn(name = "id_tarea", nullable = false)
    private Tarea tarea;

    @ManyToOne
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Usuario estudiante;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    // Ruta absoluta en disco donde EntregaService guardo el archivo fisico
    // (bajo entregas.storage.path). Nunca se expone tal cual al cliente --
    // la descarga sirve el contenido, no esta ruta.
    @Column(name = "ruta_archivo", nullable = false, length = 500)
    private String rutaArchivo;

    // PDF / DOCX / XLSX, siempre en mayusculas (ver EntregaService).
    @Column(name = "formato", nullable = false, length = 10)
    private String formato;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(name = "calificacion", precision = 4, scale = 2)
    private BigDecimal calificacion;

    @Column(name = "comentario_prof")
    private String comentarioProf;

    @Column(name = "fecha_calificacion")
    private LocalDateTime fechaCalificacion;
}
