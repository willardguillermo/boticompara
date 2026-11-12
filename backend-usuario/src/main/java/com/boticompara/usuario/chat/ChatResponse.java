package com.boticompara.usuario.chat;

import java.util.List;

/**
 * H68: respuesta de POST /chat. productos siempre es una lista (vacía si no se trató de productos).
 * respaldo es true cuando el texto lo armó el servidor porque Gemini no llegó a redactarlo.
 */
public record ChatResponse(String respuesta, List<ChatProducto> productos, boolean respaldo) {
}
