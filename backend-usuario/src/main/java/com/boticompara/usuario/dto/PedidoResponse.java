package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Reserva;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** H77-H81: formato de un pedido del dueño (API.md, sección 10). */
public record PedidoResponse(Long id, String codigo, EstadoReserva estado,
                             Long productoId, String nombreComercial, String presentacion,
                             Integer cantidad, BigDecimal precioUnitario, BigDecimal total,
                             Integer stockActual, Long disponible, String compradorNombre,
                             String motivoRechazo, Instant venceEn, Instant creadoEn, Instant actualizadoEn) {

    /**
     * disponible solo se llena en PENDIENTE (lo calcula el servicio); en los demás estados es null.
     * nombreComprador es el nombre completo de la cuenta; aquí se acorta.
     */
    public static PedidoResponse from(Reserva r, Producto p, String nombreComprador, Long disponible) {
        return new PedidoResponse(
                r.getId(), r.getCodigo(), r.getEstado(),
                r.getProductoId(),
                p == null ? null : p.getNombreComercial(),
                p == null ? null : p.getPresentacion(),
                r.getCantidad(),
                r.getPrecioUnitario(),
                r.getPrecioUnitario().multiply(BigDecimal.valueOf(r.getCantidad())).setScale(2, RoundingMode.HALF_UP),
                p == null ? null : p.getStock(),
                disponible,
                NombreCorto.de(nombreComprador),
                r.getMotivoRechazo(),
                r.getVenceEn(),
                r.getCreadoEn(),
                r.getActualizadoEn());
    }
}