package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** H20: POST /auth/registro/verificar y POST /auth/verificacion/confirmar. */
public record ConfirmarCodigoRequest(
        @NotBlank(message = "El correo es obligatorio")
        String correo,

        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "[0-9]{6}", message = "El código debe tener 6 dígitos")
        String codigo
) {
}
