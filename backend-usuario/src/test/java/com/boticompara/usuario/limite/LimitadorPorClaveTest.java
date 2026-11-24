package com.boticompara.usuario.limite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Contador en memoria por clave: cupo, espera para Retry-After, devolución y limpieza. */
class LimitadorPorClaveTest {

    /** Reloj que se puede adelantar. */
    private static final class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-11-23T15:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private RelojMovil reloj;
    private LimitadorPorClave limitador;

    @BeforeEach
    void preparar() {
        reloj = new RelojMovil();
        limitador = new LimitadorPorClave(2, Duration.ofHours(1), reloj);
    }

    @Test
    void dentroDelCupo_devuelveCero() {
        assertEquals(0, limitador.consumir("1.2.3.4"));
        assertEquals(0, limitador.consumir("1.2.3.4"));
    }

    @Test
    void alPasarElCupo_devuelveLosSegundosQueFaltan() {
        limitador.consumir("1.2.3.4");
        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(15));
        limitador.consumir("1.2.3.4");

        assertEquals(45 * 60, limitador.consumir("1.2.3.4"));
    }

    @Test
    void unRechazoNoGastaCupo() {
        limitador.consumir("a");
        limitador.consumir("a");
        limitador.consumir("a");
        limitador.consumir("a");

        reloj.ahora = reloj.ahora.plus(Duration.ofHours(1));

        assertEquals(0, limitador.consumir("a"));
        assertEquals(0, limitador.consumir("a"));
    }

    @Test
    void cadaClaveTieneSuCupo() {
        limitador.consumir("a");
        limitador.consumir("a");

        assertEquals(0, limitador.consumir("b"));
    }

    @Test
    void alTerminarLaVentana_seReinicia() {
        limitador.consumir("a");
        limitador.consumir("a");

        reloj.ahora = reloj.ahora.plus(Duration.ofHours(1));

        assertEquals(0, limitador.consumir("a"));
    }

    @Test
    void laEsperaEsComoMinimoUnSegundo() {
        limitador.consumir("a");
        limitador.consumir("a");
        reloj.ahora = reloj.ahora.plus(Duration.ofHours(1)).minusMillis(1);

        assertEquals(1, limitador.consumir("a"));
    }

    @Test
    void devolver_liberaUnUso() {
        limitador.consumir("a");
        limitador.consumir("a");

        limitador.devolver("a");

        assertEquals(0, limitador.consumir("a"));
    }

    @Test
    void devolver_sinUsosNoBajaDeCero() {
        limitador.devolver("a");
        limitador.devolver("a");
        limitador.consumir("a");
        limitador.consumir("a");

        assertEquals(3600, limitador.consumir("a"));
    }

    @Test
    void conElMapaLleno_seBorranLasVentanasVencidas() {
        for (int i = 0; i < LimitadorPorClave.MAX_CLAVES; i++) {
            limitador.consumir("ip-" + i);
        }
        reloj.ahora = reloj.ahora.plus(Duration.ofHours(1));

        limitador.consumir("nueva");

        assertEquals(1, limitador.claves());
    }

    @Test
    void conElMapaLlenoYSinVencidas_seVaciaParaNoCrecerSinTope() {
        for (int i = 0; i < LimitadorPorClave.MAX_CLAVES; i++) {
            limitador.consumir("ip-" + i);
        }

        limitador.consumir("nueva");

        assertEquals(1, limitador.claves());
    }

    @Test
    void limitesPorIp_lanzan429ConSuMensaje() {
        LimitesPorIp limites = new LimitesPorIp(1, 1, reloj);
        limites.consumirRegistro("1.2.3.4");
        limites.consumirRecuperar("1.2.3.4");

        LimiteExcedidoException registro = assertThrows(LimiteExcedidoException.class,
                () -> limites.consumirRegistro("1.2.3.4"));
        LimiteExcedidoException recuperar = assertThrows(LimiteExcedidoException.class,
                () -> limites.consumirRecuperar("1.2.3.4"));

        assertEquals(LimitesPorIp.MENSAJE_REGISTRO, registro.getMessage());
        assertEquals(3600, registro.getReintentarEnSegundos());
        assertEquals(LimitesPorIp.MENSAJE_RECUPERAR, recuperar.getMessage());
    }

    @Test
    void limitesPorIp_registroYRecuperarSonContadoresDistintos() {
        LimitesPorIp limites = new LimitesPorIp(1, 1, reloj);
        limites.consumirRegistro("1.2.3.4");

        limites.consumirRecuperar("1.2.3.4");
    }
}
