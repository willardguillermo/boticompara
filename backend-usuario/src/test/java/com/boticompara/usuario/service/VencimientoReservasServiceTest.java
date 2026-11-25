package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Reserva;
import com.boticompara.usuario.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** H84 (S4-01): el paso de las reservas activas con venceEn pasado a VENCIDA. */
@ExtendWith(MockitoExtension.class)
class VencimientoReservasServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-24T15:00:00Z");

    @Mock ReservaRepository reservas;
    @Mock ApplicationEventPublisher eventos;

    VencimientoReservasService service;

    @BeforeEach
    void preparar() {
        service = new VencimientoReservasService(reservas, eventos);
    }

    private static Reserva reserva(long id, EstadoReserva estado) {
        Reserva r = new Reserva();
        r.setId(id);
        r.setEstado(estado);
        r.setVenceEn(AHORA.minusSeconds(60));
        return r;
    }

    @Test
    void pasaAVencidaLasTresActivasYPublicaUnEventoPorCada() {
        Reserva pendiente = reserva(1L, EstadoReserva.PENDIENTE);
        Reserva confirmada = reserva(2L, EstadoReserva.CONFIRMADA);
        Reserva lista = reserva(3L, EstadoReserva.LISTA);
        when(reservas.bloquearVencidas(eq(EstadoReserva.ACTIVAS), eq(AHORA), any(Pageable.class)))
                .thenReturn(List.of(pendiente, confirmada, lista));

        assertEquals(3, service.vencer(AHORA));

        assertEquals(EstadoReserva.VENCIDA, pendiente.getEstado());
        assertEquals(EstadoReserva.VENCIDA, confirmada.getEstado());
        assertEquals(EstadoReserva.VENCIDA, lista.getEstado());
        // Primero se guardan, después los eventos (el aviso se guarda recién después del commit)
        InOrder orden = inOrder(reservas, eventos);
        orden.verify(reservas).saveAll(List.of(pendiente, confirmada, lista));
        orden.verify(eventos).publishEvent(new ReservaVencida(1L));
        orden.verify(eventos).publishEvent(new ReservaVencida(2L));
        orden.verify(eventos).publishEvent(new ReservaVencida(3L));
    }

    @Test
    void pideSoloLasActivasConLaHoraRecibidaYComoMaximo500() {
        when(reservas.bloquearVencidas(any(), any(), any(Pageable.class))).thenReturn(List.of());

        service.vencer(AHORA);

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(reservas).bloquearVencidas(eq(EstadoReserva.ACTIVAS), eq(AHORA), pagina.capture());
        assertEquals(0, pagina.getValue().getPageNumber());
        assertEquals(500, pagina.getValue().getPageSize());
        assertEquals(VencimientoReservasService.MAXIMO_POR_EJECUCION, pagina.getValue().getPageSize());
    }

    @Test
    void sinVencidasNoGuardaNiPublicaNada() {
        when(reservas.bloquearVencidas(any(), any(), any(Pageable.class))).thenReturn(List.of());

        assertEquals(0, service.vencer(AHORA));

        verify(reservas, never()).saveAll(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void elMetodoEsTransaccional() throws Exception {
        var anotacion = VencimientoReservasService.class.getMethod("vencer", Instant.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertEquals(false, anotacion == null);
        assertEquals(false, anotacion.readOnly());
    }
}
