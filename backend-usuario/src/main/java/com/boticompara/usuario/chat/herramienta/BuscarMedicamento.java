package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.boticompara.usuario.dto.MedicamentoResponse;
import com.boticompara.usuario.service.MedicamentoService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * H68: buscar_medicamento. Catálogo maestro (GET /medicamentos?q=). No genera tarjetas: un medicamento del
 * catálogo no es un producto de una botica, no tiene precio ni detalle.
 */
@Component
@RequiredArgsConstructor
public class BuscarMedicamento implements Herramienta {

    private final MedicamentoService medicamentos;

    @Override
    public String nombre() {
        return "buscar_medicamento";
    }

    @Override
    public Set<String> roles() {
        return Set.of(DUENO_BOTICA);
    }

    @Override
    public DeclaracionHerramienta declaracion() {
        return new DeclaracionHerramienta(nombre(),
                "Busca un medicamento en el catálogo maestro de BotiCompara (registro DIGEMID) por nombre comercial "
                        + "o principio activo. No devuelve precios.",
                Args.objeto(Map.of("texto",
                                Args.campo("STRING", "Nombre comercial o principio activo, mínimo 2 caracteres.")),
                        List.of("texto")));
    }

    @Override
    public ResultadoHerramienta ejecutar(ContextoChat contexto, JsonNode args) {
        String texto = Args.texto(args, "texto");
        if (texto == null || texto.length() < 2) {
            return ResultadoHerramienta.error("texto debe tener entre 2 y 100 caracteres");
        }
        List<MedicamentoResponse> todos = medicamentos.buscar(texto);
        List<Map<String, Object>> primeros = todos.stream().limit(MAX_RESULTADOS).map(m -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("nombre", ProductoParaModelo.recortar(m.nombreComercial()));
            fila.put("principioActivo", ProductoParaModelo.recortar(m.principioActivo()));
            fila.put("presentacion", ProductoParaModelo.recortar(m.presentacion()));
            fila.put("concentracion", ProductoParaModelo.recortar(m.concentracion()));
            return fila;
        }).toList();
        return ResultadoHerramienta.de(todos.size(), primeros, List.of());
    }
}
