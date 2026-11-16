package com.boticompara.usuario.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * H68: respuesta de una llamada. Si llamadas no está vacía, el modelo pide herramientas y contenido es lo que
 * hay que devolverle tal cual en la siguiente llamada (Turno.Modelo). Si está vacía, texto es la respuesta.
 */
public record GeminiRespuesta(String texto, List<LlamadaHerramienta> llamadas, JsonNode contenido) {
}
