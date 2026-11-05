package com.boticompara.usuario.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** H59: alerta de baja de precio de un favorito. ahorro = precioAnterior - precioNuevo. */
public record AlertaResponse(Long id, Long productoId, String nombreComercial, String presentacion,
                             String boticaNombre, BigDecimal precioAnterior, BigDecimal precioNuevo,
                             BigDecimal ahorro, boolean leida, Instant fecha) {
}