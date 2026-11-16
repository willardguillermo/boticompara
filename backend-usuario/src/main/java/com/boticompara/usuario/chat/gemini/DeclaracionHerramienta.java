package com.boticompara.usuario.chat.gemini;

import java.util.Map;

/**
 * H68: cómo se le describe una herramienta al modelo. parametros es el esquema de sus parámetros en el
 * formato de Gemini (tipos en mayúsculas: OBJECT, STRING, INTEGER, BOOLEAN); null si no recibe ninguno.
 */
public record DeclaracionHerramienta(String nombre, String descripcion, Map<String, Object> parametros) {
}
