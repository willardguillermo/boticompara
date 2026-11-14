package com.boticompara.usuario.chat;

import java.util.List;

/**
 * H68: respuesta de POST /chat. productos siempre es una lista (vacía si no se trató de productos).
 * respaldo es true cuando el texto lo armó el servidor porque Gemini no llegó a redactarlo.
 * ayuda es true solo cuando respondió el filtro de crisis, para que la web y la app destaquen la respuesta.
 */
public record ChatResponse(String respuesta, List<ChatProducto> productos, boolean respaldo, boolean ayuda) {
}
