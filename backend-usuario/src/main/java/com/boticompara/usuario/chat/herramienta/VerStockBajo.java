package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/** H68: ver_stock_bajo. Igual que GET /boticas/mia/productos?stockBajo=true. No recibe parámetros. */
@Component
@RequiredArgsConstructor
public class VerStockBajo implements Herramienta {

    private final CatalogoPropio catalogo;

    @Override
    public String nombre() {
        return "ver_stock_bajo";
    }

    @Override
    public Set<String> roles() {
        return Set.of(DUENO_BOTICA);
    }

    @Override
    public DeclaracionHerramienta declaracion() {
        return new DeclaracionHerramienta(nombre(),
                "Lista los productos de la botica del usuario que tienen stock bajo (stock menor o igual al mínimo).",
                null);
    }

    @Override
    public ResultadoHerramienta ejecutar(ContextoChat contexto, JsonNode args) {
        return catalogo.listar(contexto, null, true);
    }
}
