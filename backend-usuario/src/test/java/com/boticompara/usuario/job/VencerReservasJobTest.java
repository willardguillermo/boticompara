package com.boticompara.usuario.job;

import com.boticompara.usuario.service.VencimientoReservasService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H84 (S4-01): cuándo corre la tarea y qué hace si falla. */
@ExtendWith(OutputCaptureExtension.class)
class VencerReservasJobTest {

    private static final Instant AHORA = Instant.parse("2026-11-24T15:07:00Z");
    private static final String DATO = "carlos@correo.test";

    private final VencimientoReservasService vencimiento = mock(VencimientoReservasService.class);
    private final VencerReservasJob job = new VencerReservasJob(vencimiento, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void ejecutarVenceConLaHoraDelReloj(CapturedOutput log) {
        when(vencimiento.vencer(AHORA)).thenReturn(4);

        job.ejecutar();

        verify(vencimiento).vencer(AHORA);
        assertTrue(log.getOut().contains("Reservas vencidas: 4"));
    }

    @Test
    void alArrancarTambienVence() {
        job.alArrancar();

        verify(vencimiento, times(1)).vencer(AHORA);
    }

    @Test
    void sinVencidasNoEscribeNadaEnElLog(CapturedOutput log) {
        when(vencimiento.vencer(AHORA)).thenReturn(0);

        job.ejecutar();

        assertFalse(log.getOut().contains("Reservas vencidas"));
    }

    @Test
    void siFallaNoLanzaYElLogSoloTraeLaClaseDelError(CapturedOutput log) {
        when(vencimiento.vencer(AHORA)).thenThrow(new DataAccessResourceFailureException("sin base " + DATO));

        assertDoesNotThrow(job::ejecutar);

        assertTrue(log.getOut().contains("Falló el vencimiento de reservas: DataAccessResourceFailureException"));
        assertFalse(log.getOut().contains(DATO));
    }

    @Test
    void correCada15MinutosEnHoraDeLima() throws Exception {
        Scheduled s = VencerReservasJob.class.getMethod("ejecutar").getAnnotation(Scheduled.class);
        assertEquals("${app.reservas.vencer.cron:0 */15 * * * *}", s.cron());
        assertEquals("America/Lima", s.zone());

        CronExpression cron = CronExpression.parse("0 */15 * * * *");
        LocalDateTime siguiente = cron.next(LocalDateTime.of(2026, 11, 24, 10, 7));
        assertEquals(LocalDateTime.of(2026, 11, 24, 10, 15), siguiente);
        assertEquals(LocalDateTime.of(2026, 11, 24, 10, 30), cron.next(siguiente));
        assertEquals(ZoneId.of("America/Lima"), ZoneId.of(s.zone()));
    }

    @Test
    void escuchaElArranqueDeLaAplicacion() throws Exception {
        EventListener e = VencerReservasJob.class.getMethod("alArrancar").getAnnotation(EventListener.class);
        assertArrayEquals(new Class<?>[]{ApplicationReadyEvent.class}, e.value());
    }

    @Test
    void sePuedeApagarConUnaPropiedad() {
        ConditionalOnProperty c = VencerReservasJob.class.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[]{"app.reservas.vencer.habilitada"}, c.name());
        assertEquals("true", c.havingValue());
        assertTrue(c.matchIfMissing());
    }
}
