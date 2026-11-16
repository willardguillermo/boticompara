package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ChatProducto;
import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.service.BusquedaService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * H68: buscar_precios. Usa BusquedaService.buscar, el mismo de GET /productos/buscar (vista
 * v_producto_busqueda), así que el asistente ve exactamente lo que ve el comprador. No registra la búsqueda
 * (H62): eso lo hace BusquedaController, no el servicio.
 */
@Component
@RequiredArgsConstructor
public class BuscarPrecios implements Herramienta {

    private final BusquedaService busqueda;

    @Override
    public String nombre() {
        return "buscar_precios";
    }

    @Override
    public Set<String> roles() {
        return Set.of(COMPRADOR, DUENO_BOTICA);
    }

    @Override
    public DeclaracionHerramienta declaracion() {
        Map<String, Object> tipo = Args.campo("STRING",
                "nombre = por nombre comercial; principio = por principio activo. Por defecto nombre.");
        tipo.put("enum", List.of("nombre", "principio"));
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("texto", Args.campo("STRING", "Nombre del medicamento o principio activo, mínimo 2 caracteres."));
        propiedades.put("tipo", tipo);
        propiedades.put("cerca", Args.campo("BOOLEAN",
                "true para ordenar de la botica más cercana a la más lejana. Solo si el usuario compartió su ubicación."));
        return new DeclaracionHerramienta(nombre(),
                "Busca un medicamento en las boticas aprobadas y devuelve precios, stock y botica, del más barato "
                        + "al más caro.",
                Args.objeto(propiedades, List.of("texto")));
    }

    @Override
    public ResultadoHerramienta ejecutar(ContextoChat contexto, JsonNode args) {
        String texto = Args.texto(args, "texto");
        if (texto == null || texto.length() < 2) {
            return ResultadoHerramienta.error("texto debe tener entre 2 y 100 caracteres");
        }
        JsonNode tipoEnviado = args.get("tipo");
        String tipo = tipoEnviado == null || tipoEnviado.isNull() ? "nombre" : Args.texto(args, "tipo");
        if (!"nombre".equals(tipo) && !"principio".equals(tipo)) {
            return ResultadoHerramienta.error("tipo debe ser nombre o principio");
        }
        // Sin ubicación en el pedido, cerca se ignora y se ordena por precio
        boolean cerca = Args.verdadero(args, "cerca") && contexto.conUbicacion();
        Double lat = contexto.conUbicacion() ? contexto.lat() : null;
        Double lng = contexto.conUbicacion() ? contexto.lng() : null;
        List<BusquedaResponse> filas = busqueda.buscar(texto, tipo, cerca ? "distancia" : "precio_asc", lat, lng, null);
        List<BusquedaResponse> primeras = filas.stream().limit(MAX_RESULTADOS).toList();
        return ResultadoHerramienta.de(filas.size(),
                primeras.stream().map(BuscarPrecios::paraModelo).toList(),
                primeras.stream().map(BuscarPrecios::tarjeta).toList());
    }

    private static ProductoParaModelo paraModelo(BusquedaResponse f) {
        return new ProductoParaModelo(f.productoId(), f.nombreComercial(), f.principioActivo(), f.presentacion(),
                f.precio(), f.stock(), f.boticaNombre(), f.boticaDistrito(), f.distanciaKm());
    }

    private static ChatProducto tarjeta(BusquedaResponse f) {
        return new ChatProducto(f.productoId(), f.nombreComercial(), f.principioActivo(), f.presentacion(),
                f.precio(), f.stock(), null, f.boticaId(), f.boticaNombre(), f.boticaDistrito(), f.distanciaKm());
    }
}
