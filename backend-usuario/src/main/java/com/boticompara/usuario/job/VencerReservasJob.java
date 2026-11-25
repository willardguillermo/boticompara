package com.boticompara.usuario.job;

import com.boticompara.usuario.service.VencimientoReservasService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * H84 (S4-01): cada 15 minutos, hora de Lima (app.reservas.vencer.cron), y una vez al arrancar, porque el plan gratis
 * de Render se duerme y reinicia. Se apaga con app.reservas.vencer.habilitada=false.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.reservas.vencer.habilitada", havingValue = "true", matchIfMissing = true)
public class VencerReservasJob {

    private final VencimientoReservasService vencimiento;
    private final Clock reloj;

    @Autowired
    public VencerReservasJob(VencimientoReservasService vencimiento) {
        this(vencimiento, Clock.systemUTC());
    }

    /** Para las pruebas: permite fijar el reloj. */
    VencerReservasJob(VencimientoReservasService vencimiento, Clock reloj) {
        this.vencimiento = vencimiento;
        this.reloj = reloj;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        ejecutar();
    }

    @Scheduled(cron = "${app.reservas.vencer.cron:0 */15 * * * *}", zone = "America/Lima")
    public void ejecutar() {
        try {
            int vencidas = vencimiento.vencer(reloj.instant());
            if (vencidas > 0) {
                log.info("Reservas vencidas: {}", vencidas);
            }
        } catch (RuntimeException e) {
            // Solo la clase del error: el mensaje de la base podría traer datos de la reserva
            log.error("Falló el vencimiento de reservas: {}", e.getClass().getSimpleName());
        }
    }
}
