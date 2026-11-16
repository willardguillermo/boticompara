package com.boticompara.usuario.entity;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadoReservaTest {

    @Test
    void hayExactamenteLosSieteEstadosDeLaBase() {
        assertEquals(7, EstadoReserva.values().length);
    }

    @Test
    void soloPendienteConfirmadaYListaApartanUnidades() {
        assertEquals(EnumSet.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA, EstadoReserva.LISTA),
                EstadoReserva.ACTIVAS);
        assertTrue(EstadoReserva.LISTA.estaActiva());
        assertFalse(EstadoReserva.ENTREGADA.estaActiva());
        assertFalse(EstadoReserva.RECHAZADA.estaActiva());
        assertFalse(EstadoReserva.CANCELADA.estaActiva());
        assertFalse(EstadoReserva.VENCIDA.estaActiva());
    }

    @Test
    void elDuenoSoloAvanzaPorLaCadenaPendienteConfirmadaListaEntregada() {
        assertTrue(EstadoReserva.PENDIENTE.puedePasarPorEstadoA(EstadoReserva.CONFIRMADA));
        assertTrue(EstadoReserva.CONFIRMADA.puedePasarPorEstadoA(EstadoReserva.LISTA));
        assertTrue(EstadoReserva.LISTA.puedePasarPorEstadoA(EstadoReserva.ENTREGADA));

        assertFalse(EstadoReserva.PENDIENTE.puedePasarPorEstadoA(EstadoReserva.ENTREGADA));
        assertFalse(EstadoReserva.CONFIRMADA.puedePasarPorEstadoA(EstadoReserva.PENDIENTE));
        assertFalse(EstadoReserva.LISTA.puedePasarPorEstadoA(EstadoReserva.CONFIRMADA));
    }

    @Test
    void losEstadosFinalesNoSalenConNingunCambioDelDueno() {
        for (EstadoReserva final_ : new EstadoReserva[] {
                EstadoReserva.ENTREGADA, EstadoReserva.RECHAZADA,
                EstadoReserva.CANCELADA, EstadoReserva.VENCIDA}) {
            for (EstadoReserva nuevo : EstadoReserva.values()) {
                assertFalse(final_.puedePasarPorEstadoA(nuevo), final_ + " -> " + nuevo);
            }
        }
    }
}