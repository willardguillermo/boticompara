package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RestablecerRequest(
        @NotBlank(message = "El correo es obligatorio")
        String correo,

        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "[0-9]{6}", message = "El código debe tener 6 dígitos")
        String token,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password
) {
}