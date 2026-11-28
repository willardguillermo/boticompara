package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.limite.CuotaSunat;
import com.boticompara.usuario.limite.LimiteExcedidoException;
import com.boticompara.usuario.service.ConsultaRucClient.ResultadoSunat;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * H2, H20: durante el registro la cuota de SUNAT "por usuario" se cuenta por token (registro-<id>), con el mismo tope
 * global que todo lo demás. Aquí: 1 consulta por hora por clave y 2 por día en total.
 */
class RucCuotaPorTokenTest {

    private static final String[] RUCS = {"20131312955", "20601234565", "20100047218", "20100070970"};

    private int llamadas;
    private final ConsultaRucClient cliente = ruc -> {
        llamadas++;
        return ResultadoSunat.noDisponible();
    };
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-26T15:00:00Z"), ZoneOffset.UTC);
    private final RucSunatService service = new RucSunatService(cliente, new CuotaSunat(1, 2, reloj), reloj);

    @Test
    void cadaTokenTieneSuLimiteYNoTocaElDeLosUsuarios() {
        service.consultarParaValidar(RUCS[0], "registro-1");

        LimiteExcedidoException e = assertThrows(LimiteExcedidoException.class,
                () -> service.consultarParaValidar(RUCS[1], "registro-1"));
        assertEquals("Consultaste demasiados RUC. Espera un momento y vuelve a intentar", e.getMessage());

        // El usuario 1 (id numérico) no comparte contador con el token registro-1
        service.consultarParaValidar(RUCS[1], 1L);
        assertEquals(2, llamadas);
    }

    @Test
    void elTopeGlobalTambienCuentaLosTokens() {
        service.consultarParaValidar(RUCS[0], "registro-1");
        service.consultarParaValidar(RUCS[1], "registro-2");

        // Tercera del día: ya no consulta SUNAT, solo la validación local
        RucResponse r = service.consultarParaValidar(RUCS[2], "registro-3");
        assertEquals("LOCAL", r.fuente());
        assertEquals(2, llamadas);
    }

    @Test
    void enElRegistroDeLaBoticaPasadoElLimiteNoHay429SinoValidacionLocal() {
        service.consultarParaValidar(RUCS[0], "registro-1");

        RucResponse r = service.exigirValido(RUCS[1], "registro-1");

        assertEquals(true, r.valido());
        assertEquals("LOCAL", r.fuente());
        assertEquals(1, llamadas);
    }
}
