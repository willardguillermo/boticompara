package com.boticompara.usuario.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** H72-H84: los 7 estados de chk_reserva_estado en la base. */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, LISTA, ENTREGADA, RECHAZADA, CANCELADA, VENCIDA;

    /** Las que todavía aparten unidades: restan del stock disponible (API.md, sección 9). */
    public static final Set<EstadoReserva> ACTIVAS = EnumSet.of(PENDIENTE, CONFIRMADA, LISTA);

    /**
     * Cambios que hace el dueño con PATCH /pedidos/{id}/estado (API.md, sección 10).
     * RECHAZADA (PATCH /pedidos/{id}/rechazar) y CANCELADA (PATCH /reservas/{id}/cancelar)
     * tienen sus propios endpoints y reglas, así que no van aquí.
     */
    private static final Map<EstadoReserva, Set<EstadoReserva>> CAMBIOS_DEL_DUENO = Map.of(
            PENDIENTE, EnumSet.of(CONFIRMADA),
            CONFIRMADA, EnumSet.of(LISTA),
            LISTA, EnumSet.of(ENTREGADA)
    );

    public boolean estaActiva() {
        return ACTIVAS.contains(this);
    }

    /** true si el dueño puede pasar esta reserva al estado nuevo con PATCH /pedidos/{id}/estado. */
    public boolean puedePasarPorEstadoA(EstadoReserva nuevo) {
        return CAMBIOS_DEL_DUENO.getOrDefault(this, Set.of()).contains(nuevo);
    }
}