package com.boticompara.usuario.chat.gemini;

import java.util.List;

/**
 * H68: todo lo que sale hacia Gemini en una llamada: las instrucciones del sistema, la conversación y las
 * herramientas del rol. Con permitirHerramientas = false el modelo está obligado a responder con texto.
 */
public record GeminiPedido(String instrucciones, List<Turno> turnos, List<DeclaracionHerramienta> herramientas,
                           boolean permitirHerramientas) {
}
