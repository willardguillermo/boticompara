package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record RecuperarRequest(
        @NotBlank(message = "El correo es obligatorio") String correo
) {
}