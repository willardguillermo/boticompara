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
}