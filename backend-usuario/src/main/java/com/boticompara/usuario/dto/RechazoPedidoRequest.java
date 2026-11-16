package com.boticompara.usuario.dto;

/** H80: body de PATCH /pedidos/{id}/rechazar. La longitud se valida en el servicio, sin contar espacios de los bordes. */
public record RechazoPedidoRequest(String motivo) {
}