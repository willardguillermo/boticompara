package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.Producto;

import java.math.BigDecimal;

public record ProductoResponse(Long id, String nombreComercial, String principioActivo,
                               String presentacion, BigDecimal precio, Integer stock) {

    public static ProductoResponse from(Producto p) {
        return new ProductoResponse(p.getId(), p.getNombreComercial(), p.getPrincipioActivo(),
                p.getPresentacion(), p.getPrecio(), p.getStock());
    }
}
