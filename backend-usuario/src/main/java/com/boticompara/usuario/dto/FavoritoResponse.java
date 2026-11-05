package com.boticompara.usuario.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * H43 y H44: producto guardado como favorito; fecha es el momento en que se guardó.
 * H60: precioObjetivo es el precio al que el usuario quiere que le avisen (null si no puso ninguno).
 */
public record FavoritoResponse(Long productoId, String nombreComercial, String principioActivo,
                               String presentacion, BigDecimal precio, Integer stock,
                               Long boticaId, String boticaNombre, String boticaDireccion,
                               String boticaDistrito, Instant fecha, BigDecimal precioObjetivo) {
}