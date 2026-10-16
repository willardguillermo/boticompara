package com.boticompara.usuario.dto;

import java.math.BigDecimal;

/** H28: detalle de un producto para el comprador. Mismos campos que la búsqueda más el teléfono de la botica. */
public record ProductoDetalleResponse(Long productoId, String nombreComercial, String principioActivo,
                                      String presentacion, BigDecimal precio, Integer stock,
                                      Long boticaId, String boticaNombre, String boticaDireccion,
                                      String boticaDistrito, String boticaTelefono) {
}