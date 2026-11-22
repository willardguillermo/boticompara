package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ChatProducto;
import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.BoticaService;
import com.boticompara.usuario.service.BusquedaService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * H68: buscar_precios. Usa BusquedaService.buscar, el mismo de GET /productos/buscar (vista
 * v_producto_busqueda), así que el asistente ve lo mismo que el comprador; busca el texto por nombre comercial
 * y por principio activo, porque el modelo no siempre sabe cuál de los dos le dieron. No registra la búsqueda
 * (H62): eso lo hace BusquedaController, no el servicio. Si pregunta el dueño, los productos de su propia botica
 * llevan tuBotica=true para que el asistente diga "en tu botica" y lo compare con la competencia.
 */
@Component
@RequiredArgsConstructor
public class BuscarPrecios implements Herramienta {

    private final BusquedaService busqueda;
    private final BoticaService boticas;

    /** Productos de su botica que se agregan al dueño si ninguno quedó entre los 5 más baratos. */
    static final int MAX_PROPIOS_AGREGADOS = 2;

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
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("texto", Args.campo("STRING",
                "Nombre comercial o principio activo, mínimo 2 caracteres. Se busca en los dos."));
        propiedades.put("cerca", Args.campo("BOOLEAN",
                "true para ordenar de la botica más cercana a la más lejana. Solo si el usuario compartió su ubicación."));
        return new DeclaracionHerramienta(nombre(),
                "Busca un producto por nombre comercial y por principio activo en las boticas aprobadas y devuelve "
                        + "precios, stock y botica, del más barato al más caro.",
                Args.objeto(propiedades, List.of("texto")));
    }

    @Override
    public ResultadoHerramienta ejecutar(ContextoChat contexto, JsonNode args) {
        String texto = Args.texto(args, "texto");
        if (texto == null || texto.length() < 2) {
            return ResultadoHerramienta.error("texto debe tener entre 2 y 100 caracteres");
        }
        // Sin ubicación en el pedido, cerca se ignora y se ordena por precio
        boolean cerca = Args.verdadero(args, "cerca") && contexto.conUbicacion();
        List<BusquedaResponse> filas = buscarEnLosDos(texto, cerca, contexto);
        List<BusquedaResponse> elegidas = new ArrayList<>(filas.stream().limit(MAX_RESULTADOS).toList());
        Long miBotica = filas.isEmpty() ? null : boticaDelDueno(contexto);
        if (miBotica != null && elegidas.stream().noneMatch(f -> miBotica.equals(f.boticaId()))) {
            // Su botica quedó fuera de los 5 más baratos: se agregan sus productos para que pueda compararse
            filas.stream().filter(f -> miBotica.equals(f.boticaId())).limit(MAX_PROPIOS_AGREGADOS)
                    .forEach(elegidas::add);
        }
        return ResultadoHerramienta.de(filas.size(),
                elegidas.stream().map(f -> paraModelo(f, miBotica)).toList(),
                elegidas.stream().map(BuscarPrecios::tarjeta).toList());
    }

    /**
     * Mismo BusquedaService que GET /productos/buscar, una vez por nombre comercial y otra por principio activo.
     * Se juntan sin repetir productoId y se ordenan como lo hace la búsqueda: por precio y, a igual precio, por
     * botica; con cerca, por distancia (sin ubicación al final). Los dos órdenes son estables.
     */
    private List<BusquedaResponse> buscarEnLosDos(String texto, boolean cerca, ContextoChat contexto) {
        Double lat = contexto.conUbicacion() ? contexto.lat() : null;
        Double lng = contexto.conUbicacion() ? contexto.lng() : null;
        String orden = cerca ? "distancia" : "precio_asc";
        Map<Long, BusquedaResponse> unicas = new LinkedHashMap<>();
        for (String tipo : List.of("nombre", "principio")) {
            busqueda.buscar(texto, tipo, orden, lat, lng, null).forEach(f -> unicas.putIfAbsent(f.productoId(), f));
        }
        Comparator<BusquedaResponse> criterio = Comparator.comparing(BusquedaResponse::precio)
                .thenComparing(BusquedaResponse::boticaNombre, Comparator.nullsLast(Comparator.naturalOrder()));
        if (cerca) {
            criterio = Comparator.comparing(BusquedaResponse::distanciaKm,
                    Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder())).thenComparing(criterio);
        }
        return unicas.values().stream().sorted(criterio).toList();
    }

    /** id de la botica del dueño que pregunta; null para el comprador o si el dueño aún no registró su botica. */
    private Long boticaDelDueno(ContextoChat contexto) {
        if (!DUENO_BOTICA.equals(contexto.rol())) {
            return null;
        }
        try {
            Botica botica = boticas.obtenerMia(contexto.usuarioId());
            return botica == null ? null : botica.getId();
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private static ProductoParaModelo paraModelo(BusquedaResponse f, Long miBotica) {
        Boolean tuBotica = miBotica != null && miBotica.equals(f.boticaId()) ? Boolean.TRUE : null;
        return new ProductoParaModelo(f.productoId(), f.nombreComercial(), f.principioActivo(), f.presentacion(),
                f.precio(), f.stock(), f.boticaNombre(), f.boticaDistrito(), f.distanciaKm(),
                f.condicionVenta(), tuBotica);
    }

    private static ChatProducto tarjeta(BusquedaResponse f) {
        return new ChatProducto(f.productoId(), f.nombreComercial(), f.principioActivo(), f.presentacion(),
                f.precio(), f.stock(), null, f.boticaId(), f.boticaNombre(), f.boticaDistrito(), f.distanciaKm(),
                f.condicionVenta());
    }
}
