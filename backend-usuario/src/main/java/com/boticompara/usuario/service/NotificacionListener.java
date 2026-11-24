package com.boticompara.usuario.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * H103: crea los avisos del dueño después del commit de la reserva y en el mismo hilo (sin @Async).
 * Está separado de NotificacionService para atrapar también un fallo al abrir o confirmar la transacción nueva. Si el
 * error saliera de aquí, Spring lo registraría con su mensaje y toda la traza, que podrían traer datos de la base.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionListener {

    private final NotificacionService notificaciones;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCrearseReserva(ReservaCreada evento) {
        guardar(TipoNotificacion.NUEVA_RESERVA, evento.reservaId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCancelarseReserva(ReservaCancelada evento) {
        guardar(TipoNotificacion.RESERVA_CANCELADA, evento.reservaId());
    }

    /** H84 (S4-01): una por cada reserva que VencimientoReservasService pasó a VENCIDA. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alVencerseReserva(ReservaVencida evento) {
        guardar(TipoNotificacion.RESERVA_VENCIDA, evento.reservaId());
    }

    /** En el log solo van el tipo, el id de la reserva y la clase del error: nunca su mensaje. */
    private void guardar(TipoNotificacion tipo, Long reservaId) {
        try {
            notificaciones.crear(tipo, reservaId);
        } catch (DuplicateKeyException e) {
            log.info("La notificación {} de la reserva {} ya existía", tipo, reservaId);
        } catch (Exception e) {
            log.error("No se pudo guardar la notificación {} de la reserva {}: {}", tipo, reservaId,
                    e.getClass().getSimpleName());
        }
    }
}
