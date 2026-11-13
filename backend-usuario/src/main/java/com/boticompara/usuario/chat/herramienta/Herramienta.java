package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * H68: consulta de solo lectura que el modelo puede pedir. El modelo solo decide los args; el usuario, su
 * botica y la ubicación salen del ContextoChat, que arma el servidor.
 */
public interface Herramienta {

    String COMPRADOR = "COMPRADOR";
    String DUENO_BOTICA = "DUENO_BOTICA";

    /** Máximo de resultados que una herramienta le devuelve al modelo. */
    int MAX_RESULTADOS = 5;

    String nombre();

    /** Roles del token que pueden usarla. */
    Set<String> roles();

    DeclaracionHerramienta declaracion();

    /** args viene del modelo: cada herramienta valida tipos y rangos antes de usarlo. */
    ResultadoHerramienta ejecutar(ContextoChat contexto, JsonNode args);
}
