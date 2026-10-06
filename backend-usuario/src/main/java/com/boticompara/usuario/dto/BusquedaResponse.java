package com.boticompara.usuario.dto;

import java.math.BigDecimal;

public record BusquedaResponse(Long productoId, String nombreComercial, String principioActivo,
                               String presentacion, BigDecimal precio, Integer stock,
                               Long boticaId, String boticaNombre, String boticaDireccion,
                               String boticaDistrito) {
}
