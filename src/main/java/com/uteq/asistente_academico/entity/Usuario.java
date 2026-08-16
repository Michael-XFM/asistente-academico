package com.uteq.asistente_academico.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;


@Data
@Entity
@Table(name = "Usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    // WRITE_ONLY (no @JsonIgnore): el hash debe poder LLEGAR en el body de
    // POST /api/auth/registro, pero nunca debe SALIR en ninguna respuesta
    // JSON. @JsonIgnore bloqueaba las dos direcciones a la vez y rompia el
    // registro por completo (Jackson deserializaba "contrasena" como null,
    // BCryptPasswordEncoder.encode(null) explotaba con "rawPassword cannot
    // be null"). WRITE_ONLY es el idiom estandar para este caso.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "contraseña", nullable = false, length = 255)
    private String contrasena;

    @Column(name = "rol", nullable = false, length = 20)
    private String rol;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;
}
