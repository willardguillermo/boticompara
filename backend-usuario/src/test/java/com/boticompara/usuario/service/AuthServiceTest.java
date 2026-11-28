package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.LoginRequest;
import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.GoogleIdentidad;
import com.boticompara.usuario.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import com.boticompara.usuario.security.GoogleIdentidad;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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

    @Mock
    com.boticompara.usuario.security.VerificadorGoogle verificador;

    @Mock
    com.boticompara.usuario.auth.LimitadorLogin limitador;

    BCryptPasswordEncoder encoder;
    AuthService service;

    @BeforeEach
    void preparar() {
        encoder = new BCryptPasswordEncoder();
        service = new AuthService(usuarios, encoder, new JwtService(SECRETO, 3_600_000L), verificador, limitador);    }

    private Usuario cuentaActiva() {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setNombre("Ana");
        u.setCorreo(CORREO);
        u.setRol("COMPRADOR");
        u.setActivo(true);
        u.setPasswordHash(encoder.encode(CLAVE));
        u.setCorreoVerificado(true);
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

    private GoogleIdentidad googleDe(String uid, String correo) {
        return new GoogleIdentidad(uid, correo, "Ana Pérez", true);
    }

    // ---------- H20: correo sin verificar (cuentas antiguas) ----------

    @Test
    void correoSinVerificarConClaveCorrectaResponde403ConCodigo() {
        Usuario u = cuentaActiva();
        u.setCorreoVerificado(false);
        existe(u);

        ApiException ex = assertThrows(ApiException.class, login());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Verifica tu correo para entrar", ex.getMessage());
        assertEquals("CORREO_NO_VERIFICADO", ex.getCodigo());
        // La contraseña era correcta: no cuenta como intento fallido
        verify(limitador).limpiar(CORREO, "desconocida");
        verify(limitador, never()).registrarFallo(any(), any());
    }

    @Test
    void correoSinVerificarConClaveIncorrectaResponde401SinRevelarNada() {
        Usuario u = cuentaActiva();
        u.setCorreoVerificado(false);
        existe(u);

        assertNoAutoriza(() -> service.login(new LoginRequest(CORREO, "otra-clave")));
    }

    @Test
    void correoSinVerificarYSuspendidaDaPrimeroLaSuspension() {
        Usuario u = cuentaActiva();
        u.setCorreoVerificado(false);
        u.setSuspendidoHasta(Instant.now().plus(3, ChronoUnit.DAYS));
        existe(u);

        ApiException ex = assertThrows(ApiException.class, login());

        assertTrue(ex.getMessage().startsWith("Tu cuenta está suspendida hasta"));
        assertNull(ex.getCodigo());
    }

    @Test
    void googleConCuentaVinculadaEntra() {
        Usuario u = cuentaActiva();
        u.setGoogleUid("uid-1");
        when(verificador.verificar("tok")).thenReturn(googleDe("uid-1", CORREO));
        when(usuarios.findByGoogleUid("uid-1")).thenReturn(java.util.Optional.of(u));

        LoginResponse r = service.loginGoogle("tok");

        assertNotNull(r.token());
    }

    @Test
    void googleSinCuentaCreaUnaSinContrasenaYVerificada() {
        when(verificador.verificar("tok")).thenReturn(googleDe("uid-2", "nueva@gmail.com"));
        when(usuarios.findByGoogleUid("uid-2")).thenReturn(java.util.Optional.empty());
        when(usuarios.findByCorreoIgnoreCase("nueva@gmail.com")).thenReturn(java.util.Optional.empty());
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(77L);
            return u;
        });

        service.loginGoogle("tok");

        ArgumentCaptor<Usuario> captura = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(captura.capture());
        Usuario nuevo = captura.getValue();
        assertNull(nuevo.getPasswordHash());
        assertEquals("uid-2", nuevo.getGoogleUid());
        assertEquals("COMPRADOR", nuevo.getRol());
        assertTrue(nuevo.isCorreoVerificado());
        assertEquals("Ana Pérez", nuevo.getNombre());
    }

    @Test
    void googleConCorreoDeCuentaExistenteDa409SinVincular() {
        when(verificador.verificar("tok")).thenReturn(googleDe("uid-3", CORREO));
        when(usuarios.findByGoogleUid("uid-3")).thenReturn(java.util.Optional.empty());
        when(usuarios.findByCorreoIgnoreCase(CORREO)).thenReturn(java.util.Optional.of(cuentaActiva()));

        ApiException ex = assertThrows(ApiException.class, () -> service.loginGoogle("tok"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(usuarios, never()).save(any(Usuario.class));
    }

    @Test
    void googleDeCuentaDadaDeBajaDa403() {
        Usuario u = cuentaActiva();
        u.setGoogleUid("uid-4");
        u.setActivo(false);
        when(verificador.verificar("tok")).thenReturn(googleDe("uid-4", CORREO));
        when(usuarios.findByGoogleUid("uid-4")).thenReturn(java.util.Optional.of(u));

        ApiException ex = assertThrows(ApiException.class, () -> service.loginGoogle("tok"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Tu cuenta fue dada de baja", ex.getMessage());
    }

    @Test
    void googleDeCuentaSuspendidaDa403ConLaFecha() {
        Usuario u = cuentaActiva();
        u.setGoogleUid("uid-5");
        u.setSuspendidoHasta(Instant.now().plus(7, ChronoUnit.DAYS));
        when(verificador.verificar("tok")).thenReturn(googleDe("uid-5", CORREO));
        when(usuarios.findByGoogleUid("uid-5")).thenReturn(java.util.Optional.of(u));

        ApiException ex = assertThrows(ApiException.class, () -> service.loginGoogle("tok"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().startsWith("Tu cuenta está suspendida hasta "));
    }

    @Test
    void tokenInvalidoPropagaElErrorDelVerificador() {
        when(verificador.verificar("malo")).thenThrow(ApiException.unauthorized("No pudimos verificar tu cuenta de Google"));

        ApiException ex = assertThrows(ApiException.class, () -> service.loginGoogle("malo"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(usuarios, never()).findByGoogleUid(any());
    }
}