package com.boticompara.usuario.service;

import com.boticompara.usuario.service.AlertaCorreoService.AvisoBaja;
import com.boticompara.usuario.service.AlertaService.AlertaCreada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaCorreoServiceTest {

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @Mock
    CorreoService correo;

    @InjectMocks
    AlertaCorreoService service;

    private static AvisoBaja aviso(long usuario, String correo, String producto, String anterior, String nuevo) {
        return new AvisoBaja(usuario, "Carlos", correo, producto, "Tableta 500 mg x 10", "FarmaVida",
                new BigDecimal(anterior), new BigDecimal(nuevo));
    }

    private static AlertasCreadas unaAlerta() {
        return new AlertasCreadas(List.of(
                new AlertaCreada(1L, 5L, 7L, new BigDecimal("8.50"), new BigDecimal("7.50"))));
    }

    @Test
    void unaBaja_enviaUnCorreoConElAsuntoDelContrato() {
        when(correo.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        int enviados = service.enviar(List.of(aviso(5L, "carlos@x.test", "Panadol", "8.50", "7.50")));

        assertEquals(1, enviados);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq("carlos@x.test"), eq("Panadol bajó a S/ 7.50 en FarmaVida"), cuerpo.capture());
        assertTrue(cuerpo.getValue().contains("de S/ 8.50 a S/ 7.50"));
    }

    @Test
    void variasBajasDelMismoUsuario_unSoloCorreo() {
        when(correo.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        int enviados = service.enviar(List.of(
                aviso(5L, "carlos@x.test", "Panadol", "8.50", "7.50"),
                aviso(5L, "carlos@x.test", "Ibuprofeno", "3.00", "2.50"),
                aviso(6L, "rosa@x.test", "Panadol", "8.50", "7.50")));

        assertEquals(2, enviados);
        ArgumentCaptor<String> asuntos = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cuerpos = ArgumentCaptor.forClass(String.class);
        verify(correo, times(2)).enviar(anyString(), asuntos.capture(), cuerpos.capture());
        assertEquals("Bajaron de precio 2 productos de tus favoritos", asuntos.getAllValues().get(0));
        assertTrue(cuerpos.getAllValues().get(0).contains("Ibuprofeno"));
        assertEquals("Panadol bajó a S/ 7.50 en FarmaVida", asuntos.getAllValues().get(1));
    }

    @Test
    void siUnCorreoFalla_sigueConLosDemasYSoloCuentaLosEnviados() {
        when(correo.enviar(eq("a@x.test"), anyString(), anyString())).thenReturn(false);
        when(correo.enviar(eq("b@x.test"), anyString(), anyString())).thenReturn(true);

        int enviados = service.enviar(List.of(
                aviso(5L, "a@x.test", "Panadol", "8.50", "7.50"),
                aviso(6L, "b@x.test", "Panadol", "8.50", "7.50")));

        assertEquals(1, enviados);
        verify(correo, times(2)).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void alCrearseAlertas_leeLosDatosYEnviaElCorreo() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<AvisoBaja>>any()))
                .thenReturn(List.of(aviso(5L, "carlos@x.test", "Panadol", "8.50", "7.50")));
        when(correo.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        service.alCrearseAlertas(unaAlerta());

        verify(correo).enviar(eq("carlos@x.test"), anyString(), anyString());
    }

    @Test
    void siFallaLaLectura_noLanzaExcepcionNiEnviaNada() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<AvisoBaja>>any()))
                .thenThrow(new RuntimeException("sin conexion"));

        assertDoesNotThrow(() -> service.alCrearseAlertas(unaAlerta()));

        verifyNoInteractions(correo);
    }

    @Test
    void sinAlertas_noConsultaNiEnvia() {
        service.alCrearseAlertas(new AlertasCreadas(List.of()));

        verifyNoInteractions(jdbc, correo);
    }
}