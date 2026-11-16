package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotNull;

/** H79: body de PATCH /pedidos/{id}/estado. */
public record CambioEstadoPedidoRequest(
        @NotNull(message = "El estado es obligatorio") String estado
) {
}