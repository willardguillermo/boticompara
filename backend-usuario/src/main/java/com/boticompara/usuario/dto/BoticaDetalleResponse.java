package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.Botica;

import java.math.BigDecimal;

public record BoticaDetalleResponse(Long id, String nombreComercial, String ruc, String razonSocial,
                                    String direccion, String distrito, String telefono,
                                    BigDecimal latitud, BigDecimal longitud,
                                    String estado, String motivoRechazo) {

    public static BoticaDetalleResponse from(Botica b) {
        // motivoRechazo solo viaja cuando el estado es RECHAZADO (H5)
        String motivo = "RECHAZADO".equals(b.getEstado()) ? b.getMotivoRechazo() : null;
        return new BoticaDetalleResponse(b.getId(), b.getNombreComercial(), b.getRuc(), b.getRazonSocial(),
                b.getDireccion(), b.getDistrito(), b.getTelefono(), b.getLatitud(), b.getLongitud(),
                b.getEstado(), motivo);
    }
}