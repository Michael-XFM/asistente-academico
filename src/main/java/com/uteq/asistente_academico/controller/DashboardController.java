package com.uteq.asistente_academico.controller;

import com.uteq.asistente_academico.dto.ResumenAcademicoProjection;
import com.uteq.asistente_academico.dto.TareaPendienteProjection;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.DashboardRepository;
import com.uteq.asistente_academico.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost"})
public class DashboardController {

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Devuelve el resumen académico y las tareas pendientes del usuario
     * AUTENTICADO. El id_usuario se resuelve a partir del email del JWT
     * (SecurityContext), nunca se recibe como parámetro del cliente, para
     * evitar que un usuario solicite el dashboard de otro (control de
     * acceso roto, OWASP A01).
     */
    @GetMapping
    public ResponseEntity<?> obtenerDashboard(Authentication authentication) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Usuario no encontrado");
        }

        Integer idUsuario = usuarioOpt.get().getIdUsuario();

        ResumenAcademicoProjection resumen = dashboardRepository.obtenerResumenAcademico(idUsuario);
        List<TareaPendienteProjection> tareasPendientes = dashboardRepository.listarTareasPendientes(idUsuario);

        Map<String, Object> response = new HashMap<>();
        response.put("resumen", resumen);
        response.put("tareasPendientes", tareasPendientes);
        return ResponseEntity.ok(response);
    }

    /**
     * Marca TODOS los avisos pendientes del usuario AUTENTICADO como
     * leídos (actualización masiva vía fn_marcar_avisos_leidos). Igual que
     * en obtenerDashboard, el id_usuario se resuelve del JWT, nunca del
     * cliente.
     */
    @PutMapping("/avisos/marcar-leidos")
    public ResponseEntity<?> marcarAvisosLeidos(Authentication authentication) {
        Optional<Usuario> usuarioOpt = resolverUsuarioAutenticado(authentication);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Usuario no encontrado");
        }

        Integer idUsuario = usuarioOpt.get().getIdUsuario();
        Integer avisosMarcados = dashboardRepository.marcarAvisosLeidos(idUsuario);

        Map<String, Object> response = new HashMap<>();
        response.put("avisosMarcados", avisosMarcados);
        return ResponseEntity.ok(response);
    }

    private Optional<Usuario> resolverUsuarioAutenticado(Authentication authentication) {
        String email = authentication.getName();
        return usuarioService.buscarPorEmail(email);
    }
}