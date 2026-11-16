package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** H68: buscar_en_mi_catalogo. Igual que GET /boticas/mia/productos?q=. */
@Component
@RequiredArgsConstructor
public class BuscarEnMiCatalogo implements Herramienta {

    private final CatalogoPropio catalogo;

    @Override
    public String nombre() {
        return "buscar_en_mi_catalogo";
    }

    @Override
    public Set<String> roles() {
        return Set.of(DUENO_BOTICA);
    }

    @Override
    public DeclaracionHerramienta declaracion() {
        return new DeclaracionHerramienta(nombre(),
                "Busca un producto en el catálogo de la botica del usuario y devuelve su precio y su stock.",
                Args.objeto(Map.of("texto", Args.campo("STRING", "Nombre comercial o principio activo del producto.")),
                        List.of("texto")));
    }

    @Override
    public ResultadoHerramienta ejecutar(ContextoChat contexto, JsonNode args) {
        String texto = Args.texto(args, "texto");
        if (texto == null) {
            return ResultadoHerramienta.error("texto debe tener entre 1 y 100 caracteres");
        }
        return catalogo.listar(contexto, texto, false);
    }
}
