package com.boticompara.usuario.dto;

import java.math.BigDecimal;

public record BusquedaResponse(Long productoId, String nombreComercial, String principioActivo,
                               String presentacion, BigDecimal precio, Integer stock,
                               Long boticaId, String boticaNombre, String boticaDireccion,
                               String boticaDistrito, BigDecimal distanciaKm) {

    /** H27: copia con la distancia hasta la botica (null si no hay ubicación o la botica no tiene coordenadas). */
    public BusquedaResponse conDistancia(BigDecimal km) {
        return new BusquedaResponse(productoId, nombreComercial, principioActivo, presentacion, precio, stock,
                boticaId, boticaNombre, boticaDireccion, boticaDistrito, km);
    }
}