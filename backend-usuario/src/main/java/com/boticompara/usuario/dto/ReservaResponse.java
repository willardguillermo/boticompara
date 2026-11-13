package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Reserva;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** H72-H76: formato de una reserva (API.md, sección 9). */
public record ReservaResponse(Long id, String codigo, EstadoReserva estado,
                              Long productoId, String nombreComercial, String presentacion,
                              Integer cantidad, BigDecimal precioUnitario, BigDecimal total,
                              Long boticaId, String boticaNombre, String boticaDireccion,
                              String boticaDistrito, String boticaTelefono,
                              String motivoRechazo, Instant venceEn, Instant creadoEn, Instant actualizadoEn) {

    public static ReservaResponse from(Reserva r, Producto p, Botica b) {
        return new ReservaResponse(
                r.getId(), r.getCodigo(), r.getEstado(),
                r.getProductoId(),
                p == null ? null : p.getNombreComercial(),
                p == null ? null : p.getPresentacion(),
                r.getCantidad(),
                r.getPrecioUnitario(),
                r.getPrecioUnitario().multiply(BigDecimal.valueOf(r.getCantidad())).setScale(2, RoundingMode.HALF_UP),
                p == null ? null : p.getBoticaId(),
                b == null ? null : b.getNombreComercial(),
                b == null ? null : b.getDireccion(),
                b == null ? null : b.getDistrito(),
                b == null ? null : b.getTelefono(),
                r.getMotivoRechazo(),
                r.getVenceEn(),
                r.getCreadoEn(),
                r.getActualizadoEn());
    }
}