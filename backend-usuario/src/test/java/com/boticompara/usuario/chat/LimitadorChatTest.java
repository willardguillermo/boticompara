package com.boticompara.usuario.chat;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** H68: límite por usuario con un reloj que el test adelanta a mano. */
class LimitadorChatTest {

    /** Reloj de prueba: empieza el 12/11/2026 a las 10:00 de Lima (15:00 UTC). */
    private static final class Reloj extends Clock {
        private Instant ahora = Instant.parse("2026-11-12T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        void ponerEn(String instante) {
            ahora = Instant.parse(instante);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final Reloj reloj = new Reloj();

    private LimiteChatException rechazo(LimitadorChat limitador, long usuario) {
        return assertThrows(LimiteChatException.class, () -> limitador.registrar(usuario));
    }

    @Test
    void laCuartaPreguntaDelMinutoSeRechazaConLosSegundosQueFaltan() {
        LimitadorChat limitador = new LimitadorChat(3, 30, reloj);
        limitador.registrar(4L);
        reloj.avanzar(Duration.ofSeconds(10));
        limitador.registrar(4L);
        limitador.registrar(4L);
        reloj.avanzar(Duration.ofSeconds(5));

        LimiteChatException e = rechazo(limitador, 4L);

        assertEquals("Hiciste muchas preguntas seguidas. Espera un minuto y vuelve a intentar", e.getMessage());
        // La primera fue hace 15 s: se libera un lugar en 45 s
        assertEquals(45, e.getReintentarEnSegundos());
    }

    @Test
    void pasadoElMinutoVuelveAAceptar() {
        LimitadorChat limitador = new LimitadorChat(3, 30, reloj);
        limitador.registrar(4L);
        limitador.registrar(4L);
        limitador.registrar(4L);
        rechazo(limitador, 4L);
        reloj.avanzar(Duration.ofSeconds(60));

        assertDoesNotThrow(() -> limitador.registrar(4L));
    }

    @Test
    void unaPreguntaRechazadaNoCuenta() {
        LimitadorChat limitador = new LimitadorChat(1, 30, reloj);
        limitador.registrar(4L);
        reloj.avanzar(Duration.ofSeconds(59));
        rechazo(limitador, 4L);
        reloj.avanzar(Duration.ofSeconds(1));

        // Si el rechazo de hace 1 s contara, esta también se rechazaría
        assertDoesNotThrow(() -> limitador.registrar(4L));
    }

    @Test
    void cadaUsuarioTieneSuPropioLimite() {
        LimitadorChat limitador = new LimitadorChat(1, 30, reloj);
        limitador.registrar(4L);

        assertDoesNotThrow(() -> limitador.registrar(1L));
        rechazo(limitador, 4L);
    }

    @Test
    void alLlegarAlLimiteDelDiaSeRechazaHastaLaMedianocheDeLima() {
        LimitadorChat limitador = new LimitadorChat(3, 5, reloj);
        for (int i = 0; i < 5; i++) {
            limitador.registrar(4L);
            reloj.avanzar(Duration.ofMinutes(2));
        }
        // Son las 10:10 de Lima: faltan 13 h 50 min para las 00:00
        LimiteChatException e = rechazo(limitador, 4L);

        assertEquals("Llegaste al límite de preguntas de hoy. Vuelve mañana", e.getMessage());
        assertEquals(13 * 3600 + 50 * 60, e.getReintentarEnSegundos());
    }

    @Test
    void elDiaCambiaALas00DeLimaYNoALas00Utc() {
        LimitadorChat limitador = new LimitadorChat(3, 1, reloj);
        reloj.ponerEn("2026-11-12T23:30:00Z");   // 18:30 de Lima
        limitador.registrar(4L);

        reloj.ponerEn("2026-11-13T00:30:00Z");   // 19:30 de Lima: en UTC ya es otro día, en Lima no
        rechazo(limitador, 4L);

        reloj.ponerEn("2026-11-13T05:00:00Z");   // 00:00 de Lima
        assertDoesNotThrow(() -> limitador.registrar(4L));
    }

    @Test
    void usaLosLimitesConfigurados() {
        LimitadorChat limitador = new LimitadorChat(1, 30, reloj);
        limitador.registrar(4L);

        assertEquals(60, rechazo(limitador, 4L).getReintentarEnSegundos());
    }
}
