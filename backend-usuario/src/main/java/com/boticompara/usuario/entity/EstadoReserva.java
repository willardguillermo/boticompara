package com.boticompara.usuario.entity;

import java.util.EnumSet;
import java.util.Set;

/** H72-H84: los 7 estados de chk_reserva_estado en la base. */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, LISTA, ENTREGADA, RECHAZADA, CANCELADA, VENCIDA;

    /** Las que todavía aparten unidades: restan del stock disponible (API.md, sección 9). */
    public static final Set<EstadoReserva> ACTIVAS = EnumSet.of(PENDIENTE, CONFIRMADA, LISTA);

    public boolean estaActiva() {
        return ACTIVAS.contains(this);
    }
}