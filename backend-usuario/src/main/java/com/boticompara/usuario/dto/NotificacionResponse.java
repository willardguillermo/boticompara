package com.boticompara.usuario.dto;

import java.time.Instant;

/** H103: aviso para el dueño (API.md, sección 12). pedidoId = notificacion.reserva_id. Sin datos del comprador. */
public record NotificacionResponse(Long id, String tipo, String titulo, String mensaje, Long pedidoId,
                                   boolean leida, Instant creadoEn) {
}
