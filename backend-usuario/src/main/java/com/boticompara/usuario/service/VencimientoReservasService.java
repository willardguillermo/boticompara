package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Reserva;
import com.boticompara.usuario.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * H84 (S4-01): pasa a VENCIDA las reservas activas con venceEn pasado (API.md, sección 10). Lo llama VencerReservasJob.
 * Todo en una transacción: bloquea las filas (for update skip locked), las actualiza y publica un ReservaVencida por
 * cada una; NotificacionListener guarda el aviso al dueño después del commit. No toca el stock ni envía correo.
 */
@Service
@RequiredArgsConstructor
public class VencimientoReservasService {

    /** Máximo por ejecución, para no tener una transacción larga; las que sobren vencen en la siguiente. */
    static final int MAXIMO_POR_EJECUCION = 500;

    private final ReservaRepository reservas;
    private final ApplicationEventPublisher eventos;

    /** @return cuántas reservas pasaron a VENCIDA */
    @Transactional
    public int vencer(Instant ahora) {
        List<Reserva> vencidas = reservas.bloquearVencidas(EstadoReserva.ACTIVAS, ahora,
                PageRequest.of(0, MAXIMO_POR_EJECUCION));
        if (vencidas.isEmpty()) {
            return 0;
        }
        vencidas.forEach(r -> r.setEstado(EstadoReserva.VENCIDA));
        reservas.saveAll(vencidas);
        vencidas.forEach(r -> eventos.publishEvent(new ReservaVencida(r.getId())));
        return vencidas.size();
    }
}
