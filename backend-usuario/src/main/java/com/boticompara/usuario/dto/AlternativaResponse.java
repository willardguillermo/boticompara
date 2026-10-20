package com.boticompara.usuario.dto;

import java.math.BigDecimal;

/**
 * H36: producto que el comprador puede usar en lugar del consultado.
 * tipoCoincidencia: EQUIVALENTE (mismo grupo de equivalencia) o PRINCIPIO_ACTIVO (respaldo por texto).
 * ahorroPorcentaje (H37): null si no es más barato que el consultado.
 */
public record AlternativaResponse(Long productoId, String nombreComercial, String principioActivo,
                                  String presentacion, BigDecimal precio, Integer stock,
                                  Long boticaId, String boticaNombre, String boticaDireccion,
                                  String boticaDistrito, String tipoCoincidencia,
                                  BigDecimal ahorroPorcentaje) {

    public AlternativaResponse conAhorro(BigDecimal ahorro) {
        return new AlternativaResponse(productoId, nombreComercial, principioActivo, presentacion, precio, stock,
                boticaId, boticaNombre, boticaDireccion, boticaDistrito, tipoCoincidencia, ahorro);
    }
}
