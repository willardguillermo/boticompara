package com.boticompara.usuario.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** H43 y H44: producto guardado como favorito; fecha es el momento en que se guardó. */
public record FavoritoResponse(Long productoId, String nombreComercial, String principioActivo,
                               String presentacion, BigDecimal precio, Integer stock,
                               Long boticaId, String boticaNombre, String boticaDireccion,
                               String boticaDistrito, Instant fecha) {
}