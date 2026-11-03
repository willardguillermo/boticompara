package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotNull;

/** H66: activar o desactivar las alertas de baja de precio. Falta o null responde 400. */
public record NotificacionesRequest(
        @NotNull(message = "notificacionesPrecio es obligatorio (true o false)")
        Boolean notificacionesPrecio
) {
}