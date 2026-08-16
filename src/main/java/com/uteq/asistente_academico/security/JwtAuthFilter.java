package com.uteq.asistente_academico.security;

import com.uteq.asistente_academico.service.AuthService;
import com.uteq.asistente_academico.service.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private AuthService authService;

    @Autowired
    private RedisService redisService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (authService.validarToken(token) && !redisService.estaEnBlacklist(token)) {
                String email = authService.obtenerEmailDelToken(token);
                String rol = authService.obtenerRolDelToken(token);

                // El rol viaja firmado dentro del propio JWT (claim "rol"),
                // nunca se vuelve a consultar a la base de datos aqui. Se
                // mapea al formato que Spring Security espera para
                // hasRole(...)/@PreAuthorize: prefijo "ROLE_".
                List<GrantedAuthority> authorities = rol != null
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + rol))
                        : List.of();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email, null, authorities);

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}