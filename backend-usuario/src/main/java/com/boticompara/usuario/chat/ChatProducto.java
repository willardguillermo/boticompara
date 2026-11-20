package com.boticompara.usuario.chat;

import java.math.BigDecimal;

/**
 * H68: tarjeta de producto de la respuesta del chat (docs/API.md, sección 8). La arma el servidor con lo que
 * devolvieron las herramientas. stockMinimo solo tiene valor en los productos propios del dueño.
 * condicionVenta (H72): la del medicamento enlazado (SIN_RECETA, CON_RECETA, RECETA_RETENIDA) o null.
 */
public record ChatProducto(Long productoId, String nombreComercial, String principioActivo, String presentacion,
                           BigDecimal precio, Integer stock, Integer stockMinimo, Long boticaId,
                           String boticaNombre, String boticaDistrito, BigDecimal distanciaKm,
                           String condicionVenta) {
}
