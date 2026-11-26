package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BoticaRequest;
import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.limite.LimiteExcedidoException;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** H1, H15, H20: RegistroService con todo lo demás simulado (docs/API.md, sección 1). */
@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres";
    private static final String CORREO = "rosa@correo.pe";
    private static final String TOKEN = "a".repeat(64);

    @Mock UsuarioRepository usuarios;
    @Mock CodigoCorreoService codigos;
    @Mock CorreoService correo;
    @Mock BoticaService boticas;
    @Mock RucSunatService rucSunat;
    @Mock PasswordEncoder encoder;

    RegistroService service;
    CodigoCorreo verificado;

    @BeforeEach
    void preparar() {
        service = new RegistroService(usuarios, codigos, correo, boticas, rucSunat, encoder,
                new JwtService(SECRETO, 3_600_000L));
        verificado = new CodigoCorreo();
        verificado.setId(77L);
        verificado.setCorreo(CORREO);
        verificado.setProposito(Proposito.REGISTRO);
        lenient().when(encoder.encode(anyString())).thenReturn("HASH");
        lenient().when(usuarios.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId(15L);
            return u;
        });
    }

    private static BoticaRequest botica() {
        return new BoticaRequest("Botica Salud Total", "20601234565", "Salud Total Farma S.A.C.", "Av. Lima Sur 450",
                "Lurigancho-Chosica", null, new BigDecimal("-11.942500"), new BigDecimal("-76.699000"));
    }

    private static RegistroRequest comprador(String correo) {
        return new RegistroRequest(TOKEN, " Carlos Ramírez ", correo, "Boti2026!", " ", "COMPRADOR", null);
    }

    private static RegistroRequest dueno(BoticaRequest b) {
        return new RegistroRequest(TOKEN, "Rosa Quispe", CORREO, "Boti2026!", "987654321", "DUENO_BOTICA", b);
    }

    private void tokenValido() {
        when(codigos.tokenVigente(TOKEN, true)).thenReturn(verificado);
    }

    private static void error(HttpStatus status, String mensaje, Runnable accion) {
        ApiException e = assertThrows(ApiException.class, accion::run);
        assertEquals(status, e.getStatus());
        assertEquals(mensaje, e.getMessage());
    }

    // ---------- enviarCodigo ----------

    @Test
    void enviarCodigoMandaElCodigoSinTextoDelUsuario() {
        when(codigos.emitir(CORREO, Proposito.REGISTRO)).thenReturn("123456");
        when(correo.enviar(eq(CORREO), anyString(), anyString())).thenReturn(true);

        service.enviarCodigo("  Rosa@Correo.PE ");

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq(CORREO), eq("BotiCompara: tu código para crear la cuenta"), texto.capture());
        assertTrue(texto.getValue().startsWith("Hola,\n"));
        assertTrue(texto.getValue().contains("123456"));
        assertTrue(texto.getValue().contains("15 minutos"));
    }

    @Test
    void enviarCodigoAUnCorreoRegistradoDa409SinEmitir() {
        when(usuarios.existsByCorreoIgnoreCase(CORREO)).thenReturn(true);

        error(HttpStatus.CONFLICT, "El correo ya está registrado", () -> service.enviarCodigo(CORREO));

        verifyNoInteractions(codigos, correo);
    }

    @Test
    void siElCorreoNoSaleResponde503ParaDeshacerElCodigo() {
        when(codigos.emitir(CORREO, Proposito.REGISTRO)).thenReturn("123456");
        when(correo.enviar(anyString(), anyString(), anyString())).thenReturn(false);

        error(HttpStatus.SERVICE_UNAVAILABLE, "No pudimos enviar el correo. Intenta en unos minutos",
                () -> service.enviarCodigo(CORREO));
    }

    @Test
    void elLimiteDelCorreoSePropagaComo429() {
        when(codigos.emitir(CORREO, Proposito.REGISTRO)).thenThrow(new LimiteExcedidoException("espera", 30));

        assertThrows(LimiteExcedidoException.class, () -> service.enviarCodigo(CORREO));
        verifyNoInteractions(correo);
    }

    // ---------- verificarCodigo y validarRuc ----------

    @Test
    void verificarCodigoDevuelveElToken() {
        when(codigos.confirmar(CORREO, Proposito.REGISTRO, "123456")).thenReturn(verificado);
        when(codigos.emitirToken(verificado)).thenReturn(TOKEN);

        assertEquals(TOKEN, service.verificarCodigo("ROSA@correo.pe", "123456"));
    }

    @Test
    void validarRucCuentaElLimitePorTokenYNoLoGasta() {
        when(codigos.tokenVigente(TOKEN, false)).thenReturn(verificado);
        RucResponse ok = new RucResponse("20601234565", true, "RUC válido", null, null, null, "LOCAL", null, null);
        when(rucSunat.consultarParaValidar("20601234565", "registro-77")).thenReturn(ok);

        assertSame(ok, service.validarRuc(TOKEN, " 20601234565 "));
        verify(codigos, never()).gastar(any());
    }

    // ---------- registrar ----------

    @Test
    void compradorSeCreaVerificadoConSesionYGastaElToken() {
        tokenValido();

        LoginResponse r = service.registrar(comprador("Rosa@Correo.pe"));

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(guardado.capture());
        Usuario u = guardado.getValue();
        assertEquals("Carlos Ramírez", u.getNombre());
        assertEquals(CORREO, u.getCorreo());
        assertEquals("HASH", u.getPasswordHash());
        assertEquals("COMPRADOR", u.getRol());
        assertTrue(u.isCorreoVerificado());
        assertEquals(null, u.getTelefono());
        assertNotNull(r.token());
        assertEquals("Bearer", r.tipo());
        assertEquals(15L, r.usuario().id());
        verify(codigos).gastar(verificado);
        verifyNoInteractions(boticas);
    }

    @Test
    void duenoCreaLaBoticaEnLaMismaLlamadaConElLimiteDelToken() {
        tokenValido();
        BoticaRequest b = botica();

        LoginResponse r = service.registrar(dueno(b));

        assertEquals("DUENO_BOTICA", r.usuario().rol());
        verify(boticas).registrar(15L, b, "registro-77");
        verify(codigos).gastar(verificado);
    }

    @Test
    void siLaBoticaFallaNoSeGastaElTokenYElErrorSaleTalCual() {
        tokenValido();
        when(boticas.registrar(eq(15L), any(), eq("registro-77")))
                .thenThrow(ApiException.conflict("El RUC ya está registrado"));

        error(HttpStatus.CONFLICT, "El RUC ya está registrado", () -> service.registrar(dueno(botica())));

        verify(codigos, never()).gastar(any());
    }

    @Test
    void duenoSinBoticaYCompradorConBotica_400SinCrearNada() {
        tokenValido();
        error(HttpStatus.BAD_REQUEST, "Faltan los datos de la botica", () -> service.registrar(dueno(null)));

        RegistroRequest conBotica = new RegistroRequest(TOKEN, "Carlos", CORREO, "Boti2026!", null, "COMPRADOR",
                botica());
        error(HttpStatus.BAD_REQUEST, "Un comprador no registra botica", () -> service.registrar(conBotica));

        verify(usuarios, never()).save(any());
        verify(codigos, never()).gastar(any());
    }

    @Test
    void elTokenDeOtroCorreoNoSirve() {
        tokenValido();

        error(HttpStatus.BAD_REQUEST, "Tu verificación del correo venció. Vuelve a pedir un código",
                () -> service.registrar(comprador("otro@correo.pe")));

        verify(usuarios, never()).save(any());
    }

    @Test
    void unTokenInvalidoOYaUsadoNoCreaNada() {
        when(codigos.tokenVigente(TOKEN, true)).thenThrow(CodigoCorreoService.tokenInvalido());

        assertThrows(ApiException.class, () -> service.registrar(comprador(CORREO)));

        verifyNoInteractions(usuarios, boticas);
    }

    @Test
    void siElCorreoSeRegistroMientrasTanto409() {
        tokenValido();
        when(usuarios.existsByCorreoIgnoreCase(CORREO)).thenReturn(true);

        error(HttpStatus.CONFLICT, "El correo ya está registrado", () -> service.registrar(comprador(CORREO)));

        verify(usuarios, never()).save(any());
        verify(codigos, never()).gastar(any());
    }

    @Test
    void laClaveDeCuotaNoChocaConElIdDeUnUsuario() {
        assertFalse(RegistroService.claveCuota(verificado).matches("[0-9]+"));
    }
}
