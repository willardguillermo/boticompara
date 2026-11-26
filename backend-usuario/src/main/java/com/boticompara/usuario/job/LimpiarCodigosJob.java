package com.boticompara.usuario.job;

import com.boticompara.usuario.service.CodigoCorreoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * H20: borra cada hora (app.codigos.limpiar.cron, hora de Lima) los códigos de correo de más de un día: ya no sirven
 * ni para los límites. Se apaga con app.codigos.limpiar.habilitada=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.codigos.limpiar.habilitada", havingValue = "true", matchIfMissing = true)
public class LimpiarCodigosJob {

    private final CodigoCorreoService codigos;

    @Scheduled(cron = "${app.codigos.limpiar.cron:0 7 * * * *}", zone = "America/Lima")
    public void ejecutar() {
        try {
            int borrados = codigos.limpiar();
            if (borrados > 0) {
                log.info("Códigos de correo vencidos borrados: {}", borrados);
            }
        } catch (RuntimeException e) {
            // Solo la clase del error: el mensaje de la base podría traer correos
            log.error("Falló la limpieza de códigos de correo: {}", e.getClass().getSimpleName());
        }
    }
}
