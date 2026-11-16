package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ChatProducto;
import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.boticompara.usuario.dto.AlternativaResponse;
import com.boticompara.usuario.service.AlternativasService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * H68: buscar_alternativas. Usa la variante que no registra la consulta (H42), porque el producto lo elige el
 * modelo. Lee de la misma vista que la búsqueda. Las alternativas no traen distancia: el servicio no la calcula.
 */
@Component
@RequiredArgsConstructor
public class BuscarAlternativas implements Herramienta {

    private final AlternativasService alternativas;

    @Override
    public String nombre() {
        return "buscar_alternativas";
    }

    @Override
    public Set<String> roles() {
        return Set.of(COMPRADOR, DUENO_BOTICA);
    }

    @Override
    public DeclaracionHerramienta declaracion() {
        return new DeclaracionHerramienta(nombre(),
                "Busca productos equivalentes (mismo grupo o mismo principio activo) a un producto que ya salió "
                        + "en buscar_precios, del más barato al más caro.",
                Args.objeto(Map.of("productoId",
                                Args.campo("INTEGER", "El productoId de un resultado anterior de buscar_precios.")),
                        List.of("productoId")));
    }

    @Override
    public ResultadoHerramienta ejecutar(ContextoChat contexto, JsonNode args) {
        Long id = Args.idPositivo(args, "productoId");
        if (id == null) {
            return ResultadoHerramienta.error("productoId debe ser un número entero positivo");
        }
        List<AlternativaResponse> todas = alternativas.alternativasSinRegistro(id).alternativas();
        List<AlternativaResponse> primeras = todas.stream().limit(MAX_RESULTADOS).toList();
        return ResultadoHerramienta.de(todas.size(),
                primeras.stream().map(a -> new ProductoParaModelo(a.productoId(), a.nombreComercial(),
                        a.principioActivo(), a.presentacion(), a.precio(), a.stock(), a.boticaNombre(),
                        a.boticaDistrito(), null)).toList(),
                primeras.stream().map(a -> new ChatProducto(a.productoId(), a.nombreComercial(),
                        a.principioActivo(), a.presentacion(), a.precio(), a.stock(), null, a.boticaId(),
                        a.boticaNombre(), a.boticaDistrito(), null)).toList());
    }
}
