package com.boticompara.usuario.dto;

import java.math.BigDecimal;

/** condicionVenta (H72): SIN_RECETA, CON_RECETA, RECETA_RETENIDA o null; siempre el último campo. */
public record BusquedaResponse(Long productoId, String nombreComercial, String principioActivo,
                               String presentacion, BigDecimal precio, Integer stock,
                               Long boticaId, String boticaNombre, String boticaDireccion,
                               String boticaDistrito, BigDecimal boticaLatitud, BigDecimal boticaLongitud,
                               BigDecimal distanciaKm, String condicionVenta) {

    /** H27: copia con la distancia hasta la botica (null si no hay ubicación o la botica no tiene coordenadas). */
    public BusquedaResponse conDistancia(BigDecimal km) {
        return new BusquedaResponse(productoId, nombreComercial, principioActivo, presentacion, precio, stock,
                boticaId, boticaNombre, boticaDireccion, boticaDistrito, boticaLatitud, boticaLongitud, km, condicionVenta);
    }
}