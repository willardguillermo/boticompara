package com.boticompara.usuario.dto;

/**
 * H2: resultado de validar un RUC. fuente = "SUNAT" si se consultó en SUNAT, "LOCAL" si solo se validó
 * el formato y el dígito verificador (RUC mal escrito, sin sesión o SUNAT no disponible).
 * direccion y distrito son el domicilio fiscal de SUNAT (null si no vinieron o la fuente es LOCAL).
 */
public record RucResponse(String ruc, boolean valido, String mensaje,
                          String razonSocial, String estado, String condicion, String fuente,
                          String direccion, String distrito) {

    /** Solo validación local (la usa RucValidator). */
    public RucResponse(String ruc, boolean valido, String mensaje) {
        this(ruc, valido, mensaje, null, null, null, "LOCAL", null, null);
    }
}