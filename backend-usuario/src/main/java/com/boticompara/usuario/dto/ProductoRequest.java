package com.boticompara.usuario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductoRequest(
        @NotBlank(message = "El nombre comercial es obligatorio")
        @Size(max = 150, message = "El nombre comercial admite máximo 150 caracteres")
        String nombreComercial,

        @NotBlank(message = "El principio activo es obligatorio")
        @Size(max = 150, message = "El principio activo admite máximo 150 caracteres")
        String principioActivo,

        @NotBlank(message = "La presentación es obligatoria")
        @Size(max = 100, message = "La presentación admite máximo 100 caracteres")
        String presentacion,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", inclusive = false, message = "El precio debe ser mayor a 0")
        @Digits(integer = 8, fraction = 2, message = "El precio admite hasta 2 decimales")
        BigDecimal precio,

        @NotNull(message = "El stock es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stock
) {
}
