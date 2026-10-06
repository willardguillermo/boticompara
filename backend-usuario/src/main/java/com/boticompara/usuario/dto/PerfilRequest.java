package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El correo y el rol no se editan aquí. */
public record PerfilRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite máximo 100 caracteres")
        String nombre,

        @Size(max = 20, message = "El teléfono admite máximo 20 caracteres")
        String telefono,

        @Size(max = 255, message = "La dirección admite máximo 255 caracteres")
        String direccion
) {
}
