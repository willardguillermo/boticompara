package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BusquedaService {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * H22 (nombre), H23 (principio activo), H24 (orden por precio).
     * Lee de la vista v_producto_busqueda: solo productos activos, con stock y de boticas APROBADO.
     */
    @Transactional(readOnly = true)
    public List<BusquedaResponse> buscar(String q, String tipo, String orden) {
        if (q == null || q.trim().length() < 2) {
            throw ApiException.badRequest("La búsqueda requiere mínimo 2 caracteres");
        }
        // Columna y dirección salen de listas cerradas: nunca se concatena texto del usuario al SQL
        String columna = switch (tipo) {
            case "nombre" -> "nombre_comercial";
            case "principio" -> "principio_activo";
            default -> throw ApiException.badRequest("El tipo debe ser nombre o principio");
        };
        String direccion = switch (orden) {
            case "precio_asc" -> "asc";
            case "precio_desc" -> "desc";
            default -> throw ApiException.badRequest("El orden debe ser precio_asc o precio_desc");
        };

        String sql = "select producto_id, nombre_comercial, principio_activo, presentacion, precio, stock, "
                + "botica_id, botica_nombre, botica_direccion, botica_distrito "
                + "from v_producto_busqueda where lower(" + columna + ") like :patron "
                + "order by precio " + direccion + ", botica_nombre asc";

        String patron = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return jdbc.query(sql, new MapSqlParameterSource("patron", patron), (rs, i) -> new BusquedaResponse(
                rs.getLong("producto_id"),
                rs.getString("nombre_comercial"),
                rs.getString("principio_activo"),
                rs.getString("presentacion"),
                rs.getBigDecimal("precio"),
                rs.getInt("stock"),
                rs.getLong("botica_id"),
                rs.getString("botica_nombre"),
                rs.getString("botica_direccion"),
                rs.getString("botica_distrito")));
    }
}
