package com.boticompara.usuario.util;

import com.boticompara.usuario.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * H60: lee el precio objetivo del cuerpo de PUT /favoritos/{productoId}/precio-objetivo. Se lee como JsonNode para
 * distinguir "falta el campo" (400) de null (quitar el objetivo), que un record no distingue.
 */
public final class PrecioObjetivo {

    static final BigDecimal MAXIMO = new BigDecimal("100000");
    static final String MENSAJE_FALTA = "Falta precioObjetivo: envía un número o null para quitarlo";
    static final String MENSAJE_INVALIDO =
            "precioObjetivo debe ser un número mayor que 0, con máximo 2 decimales y menor que 100000";

    private PrecioObjetivo() {
    }

    /** null si viene "precioObjetivo": null (quitar); 400 si falta o no es válido. */
    public static BigDecimal leer(JsonNode cuerpo) {
        if (cuerpo == null || !cuerpo.isObject() || !cuerpo.has("precioObjetivo")) {
            throw ApiException.badRequest(MENSAJE_FALTA);
        }
        JsonNode valor = cuerpo.get("precioObjetivo");
        if (valor.isNull()) {
            return null;
        }
        if (!valor.isNumber()) {
            throw ApiException.badRequest(MENSAJE_INVALIDO);
        }
        BigDecimal precio = valor.decimalValue();
        if (precio.signum() <= 0 || precio.compareTo(MAXIMO) >= 0 || precio.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest(MENSAJE_INVALIDO);
        }
        return precio.setScale(2);
    }
}