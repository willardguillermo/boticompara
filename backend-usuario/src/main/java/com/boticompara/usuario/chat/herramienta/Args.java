package com.boticompara.usuario.chat.herramienta;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** H68: lectura de los parámetros que envía el modelo y armado de los esquemas de las herramientas. */
final class Args {

    static final int MAX_TEXTO = 100;

    private Args() {
    }

    /** Texto sin espacios a los lados; null si falta, no es texto, está vacío o pasa de 100 caracteres. */
    static String texto(JsonNode args, String campo) {
        JsonNode v = args == null ? null : args.get(campo);
        if (v == null || !v.isTextual()) {
            return null;
        }
        String texto = v.asText().strip();
        return texto.isEmpty() || texto.length() > MAX_TEXTO ? null : texto;
    }

    /** Entero positivo; null si falta o no lo es. Acepta 9 y 9.0, no "9" ni 9.5. */
    static Long idPositivo(JsonNode args, String campo) {
        JsonNode v = args == null ? null : args.get(campo);
        if (v == null || !v.isNumber()) {
            return null;
        }
        double d = v.asDouble();
        return d >= 1 && d <= Long.MAX_VALUE / 2 && d == Math.rint(d) ? (Long) (long) d : null;
    }

    static boolean verdadero(JsonNode args, String campo) {
        JsonNode v = args == null ? null : args.get(campo);
        return v != null && v.isBoolean() && v.asBoolean();
    }

    static Map<String, Object> objeto(Map<String, Object> propiedades, List<String> obligatorios) {
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "OBJECT");
        esquema.put("properties", propiedades);
        esquema.put("required", obligatorios);
        return esquema;
    }

    static Map<String, Object> campo(String tipo, String descripcion) {
        Map<String, Object> campo = new LinkedHashMap<>();
        campo.put("type", tipo);
        campo.put("description", descripcion);
        return campo;
    }
}
