package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.service.AuthService;
import com.uteq.asistente_academico.service.LoginRateLimiterService;
import com.uteq.asistente_academico.service.RedisService;
import com.uteq.asistente_academico.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuthService authService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private LoginRateLimiterService rateLimiterService;

    @PostMapping("/registro")
    public ResponseEntity<?> registro(@RequestBody Usuario usuario) {
        try {
            Usuario nuevo = usuarioService.registrar(usuario);
            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Usuario registrado exitosamente");
            response.put("usuario", nuevo.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al registrar: " + e.getMessage());
        }
    }

    /**
     * OWASP A07 (fallo de identificacion): bloquea con 429 tras 5
     * intentos fallidos consecutivos desde la misma IP.
     * OWASP A09 (fallo de registro y monitoreo): cada intento de login,
     * exitoso o fallido, se registra con IP, timestamp y el email
     * involucrado (equivalente al claim "sub" del JWT que se emitiria).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciales, HttpServletRequest request) {
        String ip = obtenerIpCliente(request);
        String email = credenciales.get("email");
        String contrasena = credenciales.get("contrasena");

        if (rateLimiterService.estaBloqueado(ip)) {
            log.warn("LOGIN BLOQUEADO | ip={} | timestamp={} | motivo=demasiados intentos fallidos", ip, Instant.now());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Demasiados intentos fallidos. Intenta de nuevo mas tarde.");
        }

        Optional<Usuario> usuario = usuarioService.buscarPorEmail(email);

        if (usuario.isEmpty()) {
            rateLimiterService.registrarIntentoFallido(ip);
            log.warn("LOGIN FALLIDO | ip={} | timestamp={} | sub={} | motivo=usuario no encontrado | intento={}",
                    ip, Instant.now(), email, rateLimiterService.intentosActuales(ip));
            return ResponseEntity.badRequest().body("Usuario no encontrado");
        }

        if (!usuarioService.verificarContrasena(contrasena, usuario.get().getContrasena())) {
            rateLimiterService.registrarIntentoFallido(ip);
            log.warn("LOGIN FALLIDO | ip={} | timestamp={} | sub={} | motivo=contrasena incorrecta | intento={}",
                    ip, Instant.now(), email, rateLimiterService.intentosActuales(ip));
            return ResponseEntity.badRequest().body("Contraseña incorrecta");
        }

        rateLimiterService.registrarLoginExitoso(ip);
        log.info("LOGIN EXITOSO | ip={} | timestamp={} | sub={}", ip, Instant.now(), email);

        String token = authService.generarToken(usuario.get());
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("rol", usuario.get().getRol());
        response.put("nombre", usuario.get().getNombre());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            redisService.agregarTokenBlacklist(token, 86400000);
            return ResponseEntity.ok("Sesión cerrada exitosamente");
        }
        return ResponseEntity.badRequest().body("Token no proporcionado");
    }

    private String obtenerIpCliente(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}