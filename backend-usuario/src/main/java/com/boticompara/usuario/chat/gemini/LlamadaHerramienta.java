package com.boticompara.usuario.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;

/** H68: herramienta que el modelo pide ejecutar. args viene del modelo: se valida antes de usarlo. */
public record LlamadaHerramienta(String id, String nombre, JsonNode args) {
}
