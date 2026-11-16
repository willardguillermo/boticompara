package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ChatProducto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H68: paraModelo es lo único que vuelve a Gemini (datos de productos y boticas, o un error);
 * tarjetas va directo a la respuesta del chat, sin pasar por el modelo.
 */
public record ResultadoHerramienta(Map<String, Object> paraModelo, List<ChatProducto> tarjetas) {

    public static ResultadoHerramienta error(String mensaje) {
        return new ResultadoHerramienta(Map.of("error", mensaje), List.of());
    }

    /** total = cuántos resultados hay en realidad; resultados trae como máximo MAX_RESULTADOS. */
    public static ResultadoHerramienta de(int total, List<?> resultados, List<ChatProducto> tarjetas) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("total", total);
        datos.put("resultados", resultados);
        return new ResultadoHerramienta(datos, tarjetas);
    }
}
