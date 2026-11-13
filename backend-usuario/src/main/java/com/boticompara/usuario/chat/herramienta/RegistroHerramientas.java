package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.boticompara.usuario.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * H68: herramientas disponibles según el rol del token. El modelo solo conoce las de su rol y, si igual pide
 * otra (o una que no existe), no se ejecuta. Un fallo de una herramienta vuelve al modelo como un resultado
 * de error: no rompe el pedido.
 */
@Slf4j
@Component
public class RegistroHerramientas {

    private final List<Herramienta> herramientas;

    public RegistroHerramientas(List<Herramienta> herramientas) {
        this.herramientas = List.copyOf(herramientas);
    }

    /** Lo que se le declara al modelo: solo las herramientas del rol. */
    public List<DeclaracionHerramienta> declaraciones(String rol) {
        return herramientas.stream().filter(h -> h.roles().contains(rol)).map(Herramienta::declaracion).toList();
    }

    public ResultadoHerramienta ejecutar(ContextoChat contexto, String nombre, JsonNode args) {
        Herramienta herramienta = herramientas.stream()
                .filter(h -> h.nombre().equals(nombre) && h.roles().contains(contexto.rol()))
                .findFirst().orElse(null);
        if (herramienta == null) {
            log.warn("El modelo pidió una herramienta que no existe o no es del rol {}", contexto.rol());
            return ResultadoHerramienta.error("Herramienta no disponible");
        }
        try {
            return herramienta.ejecutar(contexto, args);
        } catch (ApiException e) {
            // Por ejemplo "Producto no encontrado": el modelo lo explica con sus palabras
            return ResultadoHerramienta.error(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Falló la herramienta {}: {}", herramienta.nombre(), e.toString());
            return ResultadoHerramienta.error("No se pudo hacer la consulta en este momento");
        }
    }
}
