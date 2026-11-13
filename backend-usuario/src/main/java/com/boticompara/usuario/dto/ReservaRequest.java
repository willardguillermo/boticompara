package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotNull;

public record ReservaRequest(
        @NotNull(message = "El producto es obligatorio") Long productoId,
        @NotNull(message = "La cantidad es obligatoria") Integer cantidad
) {
}