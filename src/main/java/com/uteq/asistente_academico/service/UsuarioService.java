package com.uteq.asistente_academico.service;

import com.uteq.asistente_academico.audit.Auditado;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Auditado
    public Usuario registrar(Usuario usuario) {
        usuario.setContrasena(encoder.encode(usuario.getContrasena()));
        usuario.setFechaRegistro(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    public boolean verificarContrasena(String contrasena, String hash) {
        return encoder.matches(contrasena, hash);
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }
}