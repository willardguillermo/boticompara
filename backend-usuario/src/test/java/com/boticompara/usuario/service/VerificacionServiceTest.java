package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.TokenUsuario;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.TokenUsuarioRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificacionServiceTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres";

    @Mock
    UsuarioRepository usuarios;

    @Mock
    TokenUsuarioRepository tokens;

    @Mock
    CorreoService correo;

    VerificacionService service;

    @BeforeEach
    void preparar() {
        // La barra final de la URL base se ignora a propósito
        service = new VerificacionService(usuarios, tokens, correo, SECRETO, "http://localhost:8080/api/");
    }

    private Usuario usuario(boolean activo, boolean verificado) {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setNombre("Ana Torres");
        u.setCorreo("ana@correo.com");
        u.setActivo(activo);
        u.setCorreoVerificado(verificado);
        return u;
    }

    private TokenUsuario tokenGuardado(String token, Instant expira, boolean usado) {
        TokenUsuario t = new TokenUsuario();
        t.setUsuarioId(10L);
        t.setToken(service.hash(token));
        t.setTipo("VERIFICACION");
        t.setExpiraEn(expira);
        t.setUsado(usado);
        return t;
    }

    private Instant enHoras(long h) {
        return Instant.now().plus(Duration.ofHours(h));
    }

    private void conToken(String token, TokenUsuario t) {
        when(tokens.findByTokenAndTipo(service.hash(token), "VERIFICACION")).thenReturn(Optional.of(t));
    }

    // ---------- enviarAlRegistrarse ----------

    @Test
    void enviarGuardaElTokenHasheadoYMandaElEnlace() {
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(true, false)));

        service.enviarAlRegistrarse(10L);

        ArgumentCaptor<TokenUsuario> guardado = ArgumentCaptor.forClass(TokenUsuario.class);
        verify(tokens).save(guardado.capture());
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq("ana@correo.com"), anyString(), texto.capture());

        Matcher m = Pattern.compile("http://localhost:8080/api/auth/verificar\\?token=([0-9a-f]{64})")
                .matcher(texto.getValue());
        assertTrue(m.find());
        String token = m.group(1);

        TokenUsuario t = guardado.getValue();
        assertEquals(10L, t.getUsuarioId());
        assertEquals("VERIFICACION", t.getTipo());
        assertFalse(t.isUsado());
        assertEquals(service.hash(token), t.getToken());
        assertNotEquals(token, t.getToken());
        assertTrue(t.getExpiraEn().isAfter(enHoras(47)));
        assertTrue(t.getExpiraEn().isBefore(enHoras(49)));
        assertTrue(texto.getValue().contains("Hola Ana Torres"));
    }

    @Test
    void enviarNoFallaSiElCorreoNoSePudoEnviar() {
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(true, false)));
        when(correo.enviar(anyString(), anyString(), anyString())).thenReturn(false);

        assertDoesNotThrow(() -> service.enviarAlRegistrarse(10L));

        verify(tokens).save(any(TokenUsuario.class));
    }

    @Test
    void enviarNoFallaSiLaBaseDeDatosFallaAlGuardarElToken() {
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(true, false)));
        when(tokens.save(any(TokenUsuario.class))).thenThrow(new RuntimeException("base de datos caida"));

        assertDoesNotThrow(() -> service.enviarAlRegistrarse(10L));

        verify(correo, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void enviarConUnUsuarioInexistenteNoHaceNada() {
        when(usuarios.findById(10L)).thenReturn(Optional.empty());

        service.enviarAlRegistrarse(10L);

        verifyNoInteractions(tokens);
        verifyNoInteractions(correo);
    }

    // ---------- verificar ----------

    @Test
    void verificarMarcaElCorreoYUsaElToken() {
        Usuario u = usuario(true, false);
        TokenUsuario t = tokenGuardado("abc123", enHoras(10), false);
        conToken("abc123", t);
        when(usuarios.findById(10L)).thenReturn(Optional.of(u));

        assertTrue(service.verificar("  abc123  "));

        assertTrue(u.isCorreoVerificado());
        assertTrue(t.isUsado());
        verify(usuarios).save(u);
        verify(tokens).save(t);
    }

    @Test
    void verificarConUnTokenVencidoDevuelveFalse() {
        Usuario u = usuario(true, false);
        conToken("abc123", tokenGuardado("abc123", Instant.now().minusSeconds(5), false));
        when(usuarios.findById(10L)).thenReturn(Optional.of(u));

        assertFalse(service.verificar("abc123"));

        assertFalse(u.isCorreoVerificado());
        verify(usuarios, never()).save(any(Usuario.class));
    }

    @Test
    void verificarConUnTokenYaUsadoDevuelveFalse() {
        Usuario u = usuario(true, false);
        conToken("abc123", tokenGuardado("abc123", enHoras(10), true));
        when(usuarios.findById(10L)).thenReturn(Optional.of(u));

        assertFalse(service.verificar("abc123"));

        assertFalse(u.isCorreoVerificado());
    }

    @Test
    void abrirElEnlaceUnaSegundaVezConLaCuentaYaVerificadaSigueDandoTrue() {
        Usuario u = usuario(true, true);
        conToken("abc123", tokenGuardado("abc123", enHoras(10), true));
        when(usuarios.findById(10L)).thenReturn(Optional.of(u));

        assertTrue(service.verificar("abc123"));

        verify(usuarios, never()).save(any(Usuario.class));
    }

    @Test
    void verificarConUnTokenInexistenteDevuelveFalse() {
        when(tokens.findByTokenAndTipo(service.hash("nada"), "VERIFICACION")).thenReturn(Optional.empty());

        assertFalse(service.verificar("nada"));

        verifyNoInteractions(usuarios);
    }

    @Test
    void verificarSinTokenDevuelveFalseSinConsultarLaBase() {
        assertFalse(service.verificar(null));
        assertFalse(service.verificar("   "));

        verifyNoInteractions(tokens);
        verifyNoInteractions(usuarios);
    }

    @Test
    void verificarConUnUsuarioInactivoDevuelveFalse() {
        conToken("abc123", tokenGuardado("abc123", enHoras(10), false));
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(false, false)));

        assertFalse(service.verificar("abc123"));

        verify(usuarios, never()).save(any(Usuario.class));
    }
}