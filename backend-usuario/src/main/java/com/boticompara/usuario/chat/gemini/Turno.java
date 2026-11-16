package com.boticompara.usuario.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** H68: un paso de la conversación que se envía a Gemini. */
public sealed interface Turno {

    /** Texto que escribió el usuario. */
    record Usuario(String texto) implements Turno {
    }

    /** Respuesta anterior del asistente (viene del historial del cliente). */
    record Asistente(String texto) implements Turno {
    }

    /**
     * Paso intermedio del modelo dentro de un pedido (su razonamiento firmado y las herramientas que pidió).
     * Se guarda y se reenvía tal como llegó: Gemini lo exige para continuar la conversación.
     */
    record Modelo(JsonNode contenido) implements Turno {
    }

    /** Resultados de las herramientas que pidió el modelo en el paso anterior. */
    record Resultados(List<Resultado> resultados) implements Turno {
    }

    /** id es el que trajo la llamada (null si no trajo). respuesta solo lleva datos de productos y boticas. */
    record Resultado(String id, String nombre, Map<String, Object> respuesta) {
    }
}
