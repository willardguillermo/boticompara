package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.util.Distancia;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import com.boticompara.usuario.dto.ProductoDetalleResponse;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BusquedaService {

    /** H27: radio cuando radioKm llega vacío, y radio máximo permitido. */
    static final double RADIO_POR_DEFECTO_KM = 5.0;
    static final double RADIO_MAXIMO_KM = 50.0;
    private static final String MENSAJE_RADIO = "radioKm debe ser un número mayor que 0 y hasta 50";

    private final NamedParameterJdbcTemplate jdbc;

    /** Fila leída de la vista con las coordenadas de la botica, que no salen en la respuesta. */
    record FilaBusqueda(BusquedaResponse producto, BigDecimal boticaLatitud, BigDecimal boticaLongitud) {
    }

    /**
     * H22 (nombre), H23 (principio activo), H24 (orden por precio), H27 (cercanía).
     * Lee de la vista v_producto_busqueda: solo productos activos, con stock y de boticas APROBADO.
     * Sin lat y lng funciona como antes; con ellos calcula distanciaKm y, por defecto, ordena por distancia.
     */
    @Transactional(readOnly = true)
    public List<BusquedaResponse> buscar(String q, String tipo, String orden,
                                         Double lat, Double lng, String radioKm) {
        if (q == null || q.trim().length() < 2) {
            throw ApiException.badRequest("La búsqueda requiere mínimo 2 caracteres");
        }
        // Columna y dirección salen de listas cerradas: nunca se concatena texto del usuario al SQL
        String columna = switch (tipo) {
            case "nombre" -> "nombre_comercial";
            case "principio" -> "principio_activo";
            default -> throw ApiException.badRequest("El tipo debe ser nombre o principio");
        };
        boolean conUbicacion = validarUbicacion(lat, lng);
        Double radio = radio(radioKm, conUbicacion);
        String ordenFinal = orden == null || orden.isBlank() ? (conUbicacion ? "distancia" : "precio_asc") : orden;
        // Por distancia la base ordena por precio y luego Java reordena por distancia:
        // como el ordenamiento es estable, a igual distancia queda primero el más barato
        String direccion = switch (ordenFinal) {
            case "precio_asc", "distancia" -> "asc";
            case "precio_desc" -> "desc";
            default -> throw ApiException.badRequest("El orden debe ser precio_asc, precio_desc o distancia");
        };
        if ("distancia".equals(ordenFinal) && !conUbicacion) {
            throw ApiException.badRequest("El orden por distancia requiere lat y lng");
        }

        String sql = "select producto_id, nombre_comercial, principio_activo, presentacion, precio, stock, "
                + "botica_id, botica_nombre, botica_direccion, botica_distrito, botica_latitud, botica_longitud "
                + "from v_producto_busqueda where lower(" + columna + ") like :patron "
                + "order by precio " + direccion + ", botica_nombre asc";

        String patron = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        List<FilaBusqueda> filas = jdbc.query(sql, new MapSqlParameterSource("patron", patron),
                (rs, i) -> new FilaBusqueda(new BusquedaResponse(
                        rs.getLong("producto_id"),
                        rs.getString("nombre_comercial"),
                        rs.getString("principio_activo"),
                        rs.getString("presentacion"),
                        rs.getBigDecimal("precio"),
                        rs.getInt("stock"),
                        rs.getLong("botica_id"),
                        rs.getString("botica_nombre"),
                        rs.getString("botica_direccion"),
                        rs.getString("botica_distrito"),
                        null),
                        rs.getBigDecimal("botica_latitud"),
                        rs.getBigDecimal("botica_longitud")));

        if (!conUbicacion) {
            return filas.stream().map(FilaBusqueda::producto).toList();
        }
        Stream<BusquedaResponse> resultado = filas.stream().map(f -> f.producto()
                .conDistancia(distanciaKm(lat, lng, f.boticaLatitud(), f.boticaLongitud())));
        if (radio != null) {
            // Con radio, las boticas sin coordenadas quedan fuera
            BigDecimal limite = BigDecimal.valueOf(radio);
            resultado = resultado.filter(r -> r.distanciaKm() != null && r.distanciaKm().compareTo(limite) <= 0);
        }
        if ("distancia".equals(ordenFinal)) {
            // Sin coordenadas, al final
            resultado = resultado.sorted(Comparator.comparing(BusquedaResponse::distanciaKm,
                    Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder())));
        }
        return resultado.toList();
    }

    /** H27: false si no se envió ubicación; 400 si llega solo una o fuera de rango. */
    private static boolean validarUbicacion(Double lat, Double lng) {
        if (lat == null && lng == null) {
            return false;
        }
        if (lat == null || lng == null) {
            throw ApiException.badRequest("Debes enviar lat y lng juntas");
        }
        if (!(lat >= -90 && lat <= 90)) {
            throw ApiException.badRequest("La latitud debe estar entre -90 y 90");
        }
        if (!(lng >= -180 && lng <= 180)) {
            throw ApiException.badRequest("La longitud debe estar entre -180 y 180");
        }
        return true;
    }

    /** H27: null = sin filtro (no se envió radioKm); vacío = radio por defecto (5 km). */
    private static Double radio(String radioKm, boolean conUbicacion) {
        if (radioKm == null) {
            return null;
        }
        if (!conUbicacion) {
            throw ApiException.badRequest("El radio requiere lat y lng");
        }
        if (radioKm.isBlank()) {
            return RADIO_POR_DEFECTO_KM;
        }
        double r;
        try {
            r = Double.parseDouble(radioKm.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(MENSAJE_RADIO);
        }
        if (!(r > 0 && r <= RADIO_MAXIMO_KM)) {
            throw ApiException.badRequest(MENSAJE_RADIO);
        }
        return r;
    }

    private static BigDecimal distanciaKm(double lat, double lng, BigDecimal boticaLat, BigDecimal boticaLng) {
        if (boticaLat == null || boticaLng == null) {
            return null;
        }
        double km = Distancia.km(lat, lng, boticaLat.doubleValue(), boticaLng.doubleValue());
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * H28: detalle de un producto visible para el comprador.
     * Lee de la misma vista que la búsqueda: si el producto está inactivo, sin stock
     * o su botica no está APROBADO, no aparece y se responde 404.
     */
    @Transactional(readOnly = true)
    public ProductoDetalleResponse detalle(Long id) {
        String sql = "select producto_id, nombre_comercial, principio_activo, presentacion, precio, stock, "
                + "botica_id, botica_nombre, botica_direccion, botica_distrito, botica_telefono "
                + "from v_producto_busqueda where producto_id = :id";

        List<ProductoDetalleResponse> filas = jdbc.query(sql, new MapSqlParameterSource("id", id),
                (rs, i) -> new ProductoDetalleResponse(
                        rs.getLong("producto_id"),
                        rs.getString("nombre_comercial"),
                        rs.getString("principio_activo"),
                        rs.getString("presentacion"),
                        rs.getBigDecimal("precio"),
                        rs.getInt("stock"),
                        rs.getLong("botica_id"),
                        rs.getString("botica_nombre"),
                        rs.getString("botica_direccion"),
                        rs.getString("botica_distrito"),
                        rs.getString("botica_telefono")));

        if (filas.isEmpty()) {
            throw ApiException.notFound("Producto no encontrado");
        }
        return filas.get(0);
    }
}