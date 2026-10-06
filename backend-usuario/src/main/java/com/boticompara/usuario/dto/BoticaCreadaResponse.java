package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.Botica;

public record BoticaCreadaResponse(Long id, String nombreComercial, String ruc, String estado) {

    public static BoticaCreadaResponse from(Botica b) {
        return new BoticaCreadaResponse(b.getId(), b.getNombreComercial(), b.getRuc(), b.getEstado());
    }
}
