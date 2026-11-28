package com.boticompara.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** H20: POST /auth/registro/codigo y POST /auth/verificacion/codigo. Si llega un "nombre", Jackson lo ignora. */
public record CorreoRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo admite máximo 150 caracteres")
        String correo
) {
}
