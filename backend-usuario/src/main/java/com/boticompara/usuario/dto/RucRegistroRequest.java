package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** H2, H20: POST /auth/registro/validar-ruc, antes de que exista la cuenta. */
public record RucRegistroRequest(
        @NotBlank(message = "Falta el token de verificación")
        @Size(max = 100, message = "El token de verificación no es válido")
        String tokenVerificacion,

        @NotBlank(message = "El RUC es obligatorio")
        @Size(max = 20, message = "El RUC no es válido")
        String ruc
) {
}
