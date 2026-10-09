package com.boticompara.usuario.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BoticaRequest(
        @NotBlank(message = "El nombre comercial es obligatorio")
        @Size(max = 150, message = "El nombre comercial admite máximo 150 caracteres")
        String nombreComercial,

        @NotBlank(message = "El RUC es obligatorio")
        String ruc,

        @NotBlank(message = "La razón social es obligatoria")
        @Size(max = 200, message = "La razón social admite máximo 200 caracteres")
        String razonSocial,

        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 255, message = "La dirección admite máximo 255 caracteres")
        String direccion,

        @NotBlank(message = "El distrito es obligatorio")
        @Size(max = 100, message = "El distrito admite máximo 100 caracteres")
        String distrito,

        @Size(max = 20, message = "El teléfono admite máximo 20 caracteres")
        String telefono,

        @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
        @Digits(integer = 2, fraction = 6, message = "La latitud admite hasta 6 decimales")
        BigDecimal latitud,

        @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
        @Digits(integer = 3, fraction = 6, message = "La longitud admite hasta 6 decimales")
        BigDecimal longitud
) {
}