package com.boticompara.usuario.chat;

import java.util.List;

/** H68: respuesta de POST /chat. productos siempre es una lista (vacía si no se trató de productos). */
public record ChatResponse(String respuesta, List<ChatProducto> productos) {
}
