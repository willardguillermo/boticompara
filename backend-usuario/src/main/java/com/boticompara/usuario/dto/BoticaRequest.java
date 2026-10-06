package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
        String telefono
) {
}
