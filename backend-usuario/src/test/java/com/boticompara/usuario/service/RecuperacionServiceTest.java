package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.TokenUsuario;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.TokenUsuarioRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
class RecuperacionServiceTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres";
    private static final String CORREO = "carlos@boticompara.nissidev.dev";
    private static final String MENSAJE_INVALIDO = "El código no es válido o venció. Pide uno nuevo";

    @Mock
    UsuarioRepository usuarios;

    @Mock
    TokenUsuarioRepository tokens;

    @Mock
    PasswordEncoder encoder;

    @Mock
    CorreoService correo;

    RecuperacionService service;

    @BeforeEach
    void preparar() {
        service = new RecuperacionService(usuarios, tokens, encoder, correo, SECRETO);
    }

    private Usuario usuario(boolean activo) {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setNombre("Carlos Ramírez");
        u.setCorreo(CORREO);
        u.setPasswordHash("HASH_VIEJO");
        u.setActivo(activo);
        return u;
    }

    private void conUsuario(Usuario u) {
        when(usuarios.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.of(u));
    }

    private TokenUsuario token(String codigo, Instant expira, int intentos) {
        TokenUsuario t = new TokenUsuario();
        t.setUsuarioId(10L);
        t.setToken(service.hash(10L, codigo));
        t.setTipo("RECUPERACION");
        t.setExpiraEn(expira);
        t.setIntentos((short) intentos);
        return t;
    }

    private Instant enMinutos(long m) {
        return Instant.now().plus(Duration.ofMinutes(m));
    }

    private void pendientes(TokenUsuario... lista) {
        when(tokens.findByUsuarioIdAndTipoAndUsadoFalse(10L, "RECUPERACION")).thenReturn(List.of(lista));
    }

    private ApiException codigoInvalido(String codigo) {
        ApiException ex = assertThrows(ApiException.class, () -> service.restablecer(CORREO, codigo, "NuevaClave2026!"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(MENSAJE_INVALIDO, ex.getMessage());
        return ex;
    }

    // ---------- solicitar ----------

    @Test
    void solicitarGuardaElCodigoHasheadoYLoEnviaPorCorreo() {
        conUsuario(usuario(true));
        pendientes();

        service.solicitar("  " + CORREO + "  ");

        ArgumentCaptor<TokenUsuario> guardado = ArgumentCaptor.forClass(TokenUsuario.class);
        verify(tokens).save(guardado.capture());
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq(CORREO), anyString(), texto.capture());

        Matcher m = Pattern.compile("\\b([0-9]{6})\\b").matcher(texto.getValue());
        assertTrue(m.find());
        String codigo = m.group(1);

        TokenUsuario t = guardado.getValue();
        assertEquals(10L, t.getUsuarioId());
        assertEquals("RECUPERACION", t.getTipo());
        assertFalse(t.isUsado());
        assertEquals(0, t.getIntentos());
        assertEquals(service.hash(10L, codigo), t.getToken());
        assertNotEquals(codigo, t.getToken());
        assertEquals(64, t.getToken().length());
        assertTrue(t.getExpiraEn().isAfter(enMinutos(14)));
        assertTrue(t.getExpiraEn().isBefore(enMinutos(16)));
        assertTrue(texto.getValue().contains("Hola Carlos Ramírez"));
    }

    @Test
    void solicitarConUnCorreoInexistenteNoHaceNada() {
        when(usuarios.findByCorreoIgnoreCase("nadie@correo.com")).thenReturn(Optional.empty());

        service.solicitar("nadie@correo.com");

        verifyNoInteractions(tokens);
        verifyNoInteractions(correo);
    }

    @Test
    void solicitarConUnUsuarioInactivoNoHaceNada() {
        conUsuario(usuario(false));

        service.solicitar(CORREO);

        verifyNoInteractions(tokens);
        verifyNoInteractions(correo);
    }

    @Test
    void solicitarNoReenviaSiYaSePidioUnCodigoHaceMenosDeUnMinuto() {
        conUsuario(usuario(true));
        pendientes(token("111111", Instant.now().plus(Duration.ofMinutes(15)).minusSeconds(10), 0));

        service.solicitar(CORREO);

        verify(tokens, never()).save(any(TokenUsuario.class));
        verify(correo, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void solicitarInvalidaLosCodigosAnterioresYEnviaUnoNuevo() {
        conUsuario(usuario(true));
        TokenUsuario anterior = token("111111", enMinutos(5), 0);
        pendientes(anterior);

        service.solicitar(CORREO);

        assertTrue(anterior.isUsado());
        verify(tokens).save(any(TokenUsuario.class));
        verify(correo).enviar(eq(CORREO), anyString(), anyString());
    }

    // ---------- restablecer ----------

    @Test
    void restablecerConElCodigoCorrectoCambiaLaContrasena() {
        Usuario u = usuario(true);
        conUsuario(u);
        TokenUsuario t = token("123456", enMinutos(10), 0);
        pendientes(t);
        when(encoder.encode("NuevaClave2026!")).thenReturn("HASH_NUEVO");

        service.restablecer(CORREO, "123456", "NuevaClave2026!");

        assertEquals("HASH_NUEVO", u.getPasswordHash());
        assertTrue(t.isUsado());
        verify(usuarios).save(u);
    }

    @Test
    void restablecerInvalidaTambienLosOtrosCodigosPendientes() {
        conUsuario(usuario(true));
        TokenUsuario bueno = token("123456", enMinutos(10), 0);
        TokenUsuario otro = token("999999", enMinutos(3), 0);
        pendientes(bueno, otro);
        when(encoder.encode(anyString())).thenReturn("HASH_NUEVO");

        service.restablecer(CORREO, "123456", "NuevaClave2026!");

        assertTrue(bueno.isUsado());
        assertTrue(otro.isUsado());
    }

    @Test
    void unCodigoIncorrectoSumaUnIntentoYNoCambiaNada() {
        Usuario u = usuario(true);
        conUsuario(u);
        TokenUsuario t = token("123456", enMinutos(10), 0);
        pendientes(t);

        codigoInvalido("000000");

        assertEquals(1, t.getIntentos());
        assertFalse(t.isUsado());
        assertEquals("HASH_VIEJO", u.getPasswordHash());
        verify(tokens).saveAll(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void alQuintoIntentoFallidoElCodigoQuedaInvalidadoAunqueDespuesSeaCorrecto() {
        Usuario u = usuario(true);
        conUsuario(u);
        TokenUsuario t = token("123456", enMinutos(10), 4);
        pendientes(t);

        codigoInvalido("000000");
        assertEquals(5, t.getIntentos());

        codigoInvalido("123456");
        assertEquals("HASH_VIEJO", u.getPasswordHash());
        assertFalse(t.isUsado());
        verifyNoInteractions(encoder);
    }

    @Test
    void unCodigoVencidoNoSirve() {
        Usuario u = usuario(true);
        conUsuario(u);
        pendientes(token("123456", Instant.now().minusSeconds(5), 0));

        codigoInvalido("123456");

        assertEquals("HASH_VIEJO", u.getPasswordHash());
    }

    @Test
    void unCodigoYaUsadoNoSirve() {
        Usuario u = usuario(true);
        conUsuario(u);
        pendientes(); // el repositorio solo devuelve códigos sin usar

        codigoInvalido("123456");

        assertEquals("HASH_VIEJO", u.getPasswordHash());
    }

    @Test
    void unCorreoInexistenteDaElMismoMensajeQueUnCodigoIncorrecto() {
        when(usuarios.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.empty());

        codigoInvalido("123456");

        verifyNoInteractions(tokens);
    }
}