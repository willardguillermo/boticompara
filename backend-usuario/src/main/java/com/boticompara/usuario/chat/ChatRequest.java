package com.boticompara.usuario.chat;

import java.util.List;

/** H68: cuerpo de POST /chat (docs/API.md, sección 8). lat y lng no se envían a Gemini ni se guardan. */
public record ChatRequest(String mensaje, List<MensajeHistorial> historial, Double lat, Double lng) {
}
