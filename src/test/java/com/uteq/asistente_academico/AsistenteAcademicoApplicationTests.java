package com.uteq.asistente_academico;

import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.repository.UsuarioRepository;
import com.uteq.asistente_academico.service.AuthService;
import com.uteq.asistente_academico.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AsistenteAcademicoApplicationTests {

	@Autowired
	private UsuarioService usuarioService;

	@Autowired
	private AuthService authService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	private static final String EMAIL_PRUEBA = "michael@uteq.edu.ec";
	private static final String CLAVE_PRUEBA = "admin123";

	/**
	 * Antes de cada prueba, se asegura de que el usuario de prueba exista.
	 * Corregido: las pruebas originales asumian que "michael@uteq.edu.ec"
	 * ya existia en la base de datos (dato pre-sembrado manualmente), lo
	 * que las hacia fallar en una base de datos limpia (ej. recien creada
	 * por Flyway en una maquina nueva). Ahora las pruebas crean su propio
	 * dato de prueba si no existe, de forma idempotente (no falla si ya
	 * existe de una corrida anterior).
	 */
	@BeforeEach
	void asegurarUsuarioDePrueba() {
		if (usuarioRepository.findByEmail(EMAIL_PRUEBA).isEmpty()) {
			Usuario u = new Usuario();
			u.setNombre("Michael Prueba");
			u.setEmail(EMAIL_PRUEBA);
			u.setContrasena(CLAVE_PRUEBA); // usuarioService.registrar() la hashea
			u.setRol("estudiante");
			usuarioService.registrar(u);
		}
	}

	@Test
	void loginExitoso() {
		var usuario = usuarioRepository.findByEmail(EMAIL_PRUEBA);
		assertTrue(usuario.isPresent());
		assertTrue(usuarioService.verificarContrasena(CLAVE_PRUEBA, usuario.get().getContrasena()));
	}

	@Test
	void loginConClaveIncorrecta() {
		var usuario = usuarioRepository.findByEmail(EMAIL_PRUEBA);
		assertTrue(usuario.isPresent());
		assertFalse(usuarioService.verificarContrasena("clavemalaa", usuario.get().getContrasena()));
	}

	@Test
	void registroConEmailDuplicado() {
		assertThrows(Exception.class, () -> {
			Usuario u = new Usuario();
			u.setNombre("Duplicado");
			u.setEmail(EMAIL_PRUEBA);
			u.setContrasena("123456");
			u.setRol("estudiante");
			usuarioService.registrar(u);
		});
	}

	@Test
	void accesoSinToken() {
		assertFalse(authService.validarToken("token_invalido"));
	}

	@Test
	void accesoConTokenValido() {
		var usuario = usuarioRepository.findByEmail(EMAIL_PRUEBA);
		assertTrue(usuario.isPresent());
		String token = authService.generarToken(usuario.get());
		assertTrue(authService.validarToken(token));
	}
}