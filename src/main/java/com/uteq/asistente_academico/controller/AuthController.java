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
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Formato basico de email. Un payload de inyeccion tipo ' OR '1'='1
    // no coincide con este patron y se rechaza antes de llegar a
    // cualquier consulta a la base de datos (OWASP A03).
    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

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
     * OWASP A03 (inyeccion): el campo email se valida contra un formato
     * estricto ANTES de tocar la base de datos. Un payload tipo
     * ' OR '1'='1 no es un email valido y se rechaza con 422 +
     * ProblemDetail, sin llegar nunca a ejecutarse como parte de una
     * consulta (ademas, la consulta subyacente usa Spring Data JPA con
     * parametros, nunca concatenacion, ver A.2).
     *
     * OWASP A07 (fallo de identificacion): bloquea con 429 tras 5
     * intentos fallidos consecutivos desde la misma IP.
     *
     * OWASP A09 (fallo de registro y monitoreo): cada intento de login,
     * exitoso o fallido, se registra con IP, timestamp y el email
     * involucrado.
     *
     * Bloque A.1: todos los errores responden como ProblemDetail
     * (RFC 7807), no como texto plano.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciales, HttpServletRequest request) {
        String ip = obtenerIpCliente(request);
        String email = credenciales.get("email");
        String contrasena = credenciales.get("contrasena");
        String instancia = request.getRequestURI();

        if (email == null || !EMAIL_VALIDO.matcher(email).matches()) {
            log.warn("LOGIN RECHAZADO | ip={} | timestamp={} | motivo=formato de email invalido | valor_recibido={}",
                    ip, Instant.now(), email);
            ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "El campo 'email' no tiene un formato de correo valido."
            );
            problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/formato-invalido"));
            problema.setTitle("Formato de entrada invalido");
            problema.setInstance(URI.create(instancia));
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problema);
        }

        if (rateLimiterService.estaBloqueado(ip)) {
            log.warn("LOGIN BLOQUEADO | ip={} | timestamp={} | motivo=demasiados intentos fallidos", ip, Instant.now());
            ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Intenta de nuevo mas tarde."
            );
            problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/demasiados-intentos"));
            problema.setTitle("Limite de intentos excedido");
            problema.setInstance(URI.create(instancia));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problema);
        }

        Optional<Usuario> usuario = usuarioService.buscarPorEmail(email);

        if (usuario.isEmpty()) {
            rateLimiterService.registrarIntentoFallido(ip);
            log.warn("LOGIN FALLIDO | ip={} | timestamp={} | sub={} | motivo=usuario no encontrado | intento={}",
                    ip, Instant.now(), email, rateLimiterService.intentosActuales(ip));
            return construirErrorCredenciales(instancia);
        }

        if (!usuarioService.verificarContrasena(contrasena, usuario.get().getContrasena())) {
            rateLimiterService.registrarIntentoFallido(ip);
            log.warn("LOGIN FALLIDO | ip={} | timestamp={} | sub={} | motivo=contrasena incorrecta | intento={}",
                    ip, Instant.now(), email, rateLimiterService.intentosActuales(ip));
            return construirErrorCredenciales(instancia);
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

    private ResponseEntity<ProblemDetail> construirErrorCredenciales(String instancia) {
        // Mensaje generico a proposito: no distingue "usuario no
        // encontrado" de "contrasena incorrecta" en la respuesta al
        // cliente, para no facilitar enumeracion de usuarios validos.
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Email o contraseña incorrectos."
        );
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/credenciales-invalidas"));
        problema.setTitle("Credenciales invalidas");
        problema.setInstance(URI.create(instancia));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }

    private String obtenerIpCliente(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}