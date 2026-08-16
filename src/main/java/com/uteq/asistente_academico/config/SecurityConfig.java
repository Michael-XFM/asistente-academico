package com.uteq.asistente_academico.config;

import com.uteq.asistente_academico.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
// Habilita @PreAuthorize en los controladores (Bloque de roles y permisos):
// permite proteger endpoints puntuales segun el rol del usuario autenticado,
// ademas de las reglas generales por ruta ya definidas mas abajo. El 403
// que arroja @PreAuthorize se formatea como ProblemDetail en
// GlobalExceptionHandler.manejarAccesoDenegado(...), no aqui: la excepcion
// se lanza dentro del propio DispatcherServlet (durante la invocacion del
// metodo del controlador), por lo que el @RestControllerAdvice la resuelve
// antes de que llegue al filtro de seguridad.
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/*.html", "/css/**", "/js/**", "/index.html").permitAll()
                        // Documentacion OpenAPI/Swagger: publica a proposito, igual que en
                        // cualquier API publica bien documentada. No expone datos, solo el
                        // contrato de la API (Bloque B.1 de esta entrega).
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // OWASP A05 (mala configuracion de seguridad): cabeceras
                // explicitas de defensa en profundidad. X-Content-Type-Options
                // y X-Frame-Options ya vienen activas por defecto en Spring
                // Security, se dejan explicitas aqui para que quede
                // documentado en el codigo, no solo "porque asi viene".
                .headers(headers -> headers
                        .contentTypeOptions(contentTypeOptions -> {})
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                                "frame-ancestors 'none'; " +
                                                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                                                "font-src 'self' https://fonts.gstatic.com; " +
                                                "script-src 'self' 'unsafe-inline'; " +
                                                // Swagger UI (springdoc) dibuja sus iconos como SVG
                                                // inline en data URIs; sin este permiso, el navegador
                                                // los bloquea por CSP aunque la pagina siga funcionando.
                                                "img-src 'self' data:"
                                ))
                        // HSTS solo tiene efecto real sobre HTTPS; se declara
                        // igual para cuando el sistema tenga TLS (ver A02,
                        // pendiente en esta entrega — ver docs/mediciones/sec/).
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}