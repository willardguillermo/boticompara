package com.boticompara.usuario.job;

import com.boticompara.usuario.service.AlertaStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** H12: alerta diaria de stock bajo. Por defecto a las 8:00 hora de Lima (app.alertas.stock.cron). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.alertas.stock.habilitada", havingValue = "true", matchIfMissing = true)
public class AlertaStockJob {

    private final AlertaStockService alertas;

    @Scheduled(cron = "${app.alertas.stock.cron:0 0 8 * * *}", zone = "America/Lima")
    public void ejecutar() {
        try {
            alertas.enviarAlertas();
        } catch (RuntimeException e) {
            log.error("Falló la alerta diaria de stock bajo", e);
        }
    }
}