package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.LoginRequest;
import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres";
    private static final String CORREO = "ana@correo.com";
    private static final String CLAVE = "Boti2026!";
    /** Mismo mensaje para todos los casos: no revela si el correo existe ni si la cuenta está suspendida. */
    private static final String MENSAJE = "Correo o contraseña incorrectos";

    @Mock
    UsuarioRepository usuarios;

    BCryptPasswordEncoder encoder;
    AuthService service;

    @BeforeEach
    void preparar() {
        encoder = new BCryptPasswordEncoder();
        service = new AuthService(usuarios, encoder, new JwtService(SECRETO, 3_600_000L));
    }

    private Usuario cuentaActiva() {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setNombre("Ana");
        u.setCorreo(CORREO);
        u.setRol("COMPRADOR");
        u.setActivo(true);
        u.setPasswordHash(encoder.encode(CLAVE));
        return u;
    }

    private void existe(Usuario u) {
        when(usuarios.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.of(u));
    }

    private Executable login() {
        return () -> service.login(new LoginRequest(CORREO, CLAVE));
    }

    private void assertNoAutoriza(Executable accion) {
        ApiException ex = assertThrows(ApiException.class, accion);
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(MENSAJE, ex.getMessage());
    }

    @Test
    void cuentaActivaConClaveCorrectaRecibeToken() {
        existe(cuentaActiva());

        LoginResponse r = service.login(new LoginRequest(CORREO, CLAVE));

        assertNotNull(r.token());
        assertEquals("Bearer", r.tipo());
        assertEquals(CORREO, r.usuario().correo());
    }

    @Test
    void suspensionVencidaSiPermiteEntrar() {
        Usuario u = cuentaActiva();
        u.setSuspendidoHasta(Instant.now().minus(1, ChronoUnit.DAYS));
        u.setMotivoEstado("Suspensión de prueba");
        existe(u);

        LoginResponse r = service.login(new LoginRequest(CORREO, CLAVE));

        assertNotNull(r.token());
    }

    @Test
    void cuentaSinContrasenaNoEntraNiRevientaElServicio() {
        // H67: cuenta creada solo con Google, password_hash = null
        Usuario u = cuentaActiva();
        u.setPasswordHash(null);
        existe(u);

        assertNoAutoriza(login());
    }

    @Test
    void claveIncorrectaNoEntra() {
        existe(cuentaActiva());

        assertNoAutoriza(() -> service.login(new LoginRequest(CORREO, "otra-clave")));
    }

    @Test
    void correoInexistenteNoEntra() {
        when(usuarios.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.empty());

        assertNoAutoriza(login());
    }

    @Test
    void cuentaSuspendidaConClaveCorrectaResponde403ConLaFecha() {
        Usuario u = cuentaActiva();
        u.setSuspendidoHasta(Instant.now().plus(7, ChronoUnit.DAYS));
        existe(u);

        ApiException ex = assertThrows(ApiException.class, login());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().startsWith("Tu cuenta está suspendida hasta "));
    }

    @Test
    void cuentaSuspendidaConClaveIncorrectaResponde401SinRevelarNada() {
        Usuario u = cuentaActiva();
        u.setSuspendidoHasta(Instant.now().plus(7, ChronoUnit.DAYS));
        existe(u);

        assertNoAutoriza(() -> service.login(new LoginRequest(CORREO, "otra-clave")));
    }

    @Test
    void cuentaDadaDeBajaConClaveCorrectaResponde403() {
        Usuario u = cuentaActiva();
        u.setActivo(false);
        existe(u);

        ApiException ex = assertThrows(ApiException.class, login());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Tu cuenta fue dada de baja", ex.getMessage());
    }
}