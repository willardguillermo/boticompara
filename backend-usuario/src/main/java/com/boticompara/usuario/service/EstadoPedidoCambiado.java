package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.EstadoReserva;

/** H83: se publica al pasar a LISTA o RECHAZADA. El correo al comprador sale después del commit. */
public record EstadoPedidoCambiado(Long reservaId, EstadoReserva estado) {
}