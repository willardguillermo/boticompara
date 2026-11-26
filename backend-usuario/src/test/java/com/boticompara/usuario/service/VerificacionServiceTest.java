package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import com.boticompara.usuario.entity.TokenUsuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.limite.LimiteExcedidoException;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Mock
    CodigoCorreoService codigos;

    VerificacionService service;

    @BeforeEach
    void preparar() {
        service = new VerificacionService(usuarios, tokens, correo, codigos, SECRETO);
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

    // ---------- enviarCodigo (POST /auth/verificacion/codigo) ----------

    private Usuario conClave(Usuario u) {
        u.setPasswordHash("$2a$10$hash");
        return u;
    }

    @Test
    void enviarCodigoACuentaSinVerificarMandaElCodigo() {
        when(usuarios.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(conClave(usuario(true, false))));
        when(codigos.emitir("ana@correo.com", Proposito.VERIFICACION)).thenReturn("123456");

        service.enviarCodigo("  Ana@Correo.com ");

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq("ana@correo.com"), anyString(), texto.capture());
        assertTrue(texto.getValue().contains("123456"));
    }

    @Test
    void enviarCodigoNoHaceNadaSiNoHayCuentaOYaEstaVerificadaOEsDeGoogleOEstaDeBaja() {
        when(usuarios.findByCorreoIgnoreCase("nadie@correo.com")).thenReturn(Optional.empty());
        service.enviarCodigo("nadie@correo.com");

        when(usuarios.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(conClave(usuario(true, true))));
        service.enviarCodigo("ana@correo.com");

        when(usuarios.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(usuario(true, false)));
        service.enviarCodigo("ana@correo.com");

        when(usuarios.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(conClave(usuario(false, false))));
        service.enviarCodigo("ana@correo.com");

        verifyNoInteractions(codigos);
        verifyNoInteractions(correo);
    }

    @Test
    void enviarCodigoConElLimiteDelCorreoNoFallaNiEnvia() {
        when(usuarios.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(conClave(usuario(true, false))));
        when(codigos.emitir("ana@correo.com", Proposito.VERIFICACION))
                .thenThrow(new LimiteExcedidoException("espera", 60));

        assertDoesNotThrow(() -> service.enviarCodigo("ana@correo.com"));

        verifyNoInteractions(correo);
    }

    // ---------- confirmarCodigo (POST /auth/verificacion/confirmar) ----------

    @Test
    void confirmarCodigoMarcaElCorreoYCierraElCodigo() {
        Usuario u = conClave(usuario(true, false));
        CodigoCorreo fila = new CodigoCorreo();
        when(codigos.confirmar("ana@correo.com", Proposito.VERIFICACION, "123456")).thenReturn(fila);
        when(usuarios.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(u));

        service.confirmarCodigo("ANA@correo.com", "123456");

        assertTrue(u.isCorreoVerificado());
        verify(usuarios).save(u);
        verify(codigos).gastar(fila);
    }

    @Test
    void confirmarCodigoIncorrectoNoVerificaNada() {
        when(codigos.confirmar("ana@correo.com", Proposito.VERIFICACION, "000000"))
                .thenThrow(CodigoCorreoService.codigoInvalido());

        ApiException e = assertThrows(ApiException.class, () -> service.confirmarCodigo("ana@correo.com", "000000"));

        assertEquals(CodigoCorreoService.MENSAJE_CODIGO, e.getMessage());
        verifyNoInteractions(usuarios);
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