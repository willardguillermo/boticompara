package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.AlertaResponse;
import com.boticompara.usuario.dto.ResumenAlertasResponse;
import com.boticompara.usuario.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * H59: alertas de baja de precio (tabla alerta_precio, con JDBC como favoritos). Los datos del producto se leen de
 * producto y botica, no de la vista de búsqueda: la alerta se conserva aunque el producto deje de verse.
 */
@Service
@RequiredArgsConstructor
public class AlertaService {

    static final int MAX_ALERTAS = 100;

    private final NamedParameterJdbcTemplate jdbc;

    /** soloNoLeidas: null (por defecto false), "true" o "false"; cualquier otro valor responde 400. */
    @Transactional(readOnly = true)
    public List<AlertaResponse> listar(Long usuarioId, String soloNoLeidas) {
        String sql = "select a.id, a.producto_id, p.nombre_comercial, p.presentacion, b.nombre_comercial as botica_nombre, "
                + "a.precio_anterior, a.precio_nuevo, a.leida, a.fecha "
                + "from alerta_precio a join producto p on p.id = a.producto_id join botica b on b.id = p.botica_id "
                + "where a.usuario_id = :usuario "
                + (soloNoLeidas(soloNoLeidas) ? "and not a.leida " : "")
                + "order by a.fecha desc, a.id desc limit " + MAX_ALERTAS;
        return jdbc.query(sql, new MapSqlParameterSource("usuario", usuarioId), (rs, i) -> {
            BigDecimal anterior = rs.getBigDecimal("precio_anterior");
            BigDecimal nuevo = rs.getBigDecimal("precio_nuevo");
            return new AlertaResponse(
                    rs.getLong("id"),
                    rs.getLong("producto_id"),
                    rs.getString("nombre_comercial"),
                    rs.getString("presentacion"),
                    rs.getString("botica_nombre"),
                    anterior,
                    nuevo,
                    anterior.subtract(nuevo),
                    rs.getBoolean("leida"),
                    rs.getTimestamp("fecha").toInstant());
        });
    }

    @Transactional(readOnly = true)
    public ResumenAlertasResponse resumen(Long usuarioId) {
        Long noLeidas = jdbc.queryForObject(
                "select count(*) from alerta_precio where usuario_id = :usuario and not leida",
                new MapSqlParameterSource("usuario", usuarioId), Long.class);
        return new ResumenAlertasResponse(noLeidas == null ? 0 : noLeidas);
    }

    /** Idempotente: si ya estaba leída no pasa nada. 404 si no existe o es de otro usuario. */
    @Transactional
    public void marcarLeida(Long usuarioId, Long alertaId) {
        int filas = jdbc.update("update alerta_precio set leida = true where id = :id and usuario_id = :usuario",
                new MapSqlParameterSource("id", alertaId).addValue("usuario", usuarioId));
        if (filas == 0) {
            throw ApiException.notFound("Alerta no encontrada");
        }
    }

    @Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        jdbc.update("update alerta_precio set leida = true where usuario_id = :usuario and not leida",
                new MapSqlParameterSource("usuario", usuarioId));
    }

    private static boolean soloNoLeidas(String valor) {
        if (valor == null) {
            return false;
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw ApiException.badRequest("soloNoLeidas debe ser true o false");
        };
    }
    /** Favorito al que podría avisarse de una baja de precio. */
    record Interesado(Long usuarioId, BigDecimal precioObjetivo, BigDecimal ultimoPrecioAvisado) {
    }

    /** Alerta recién creada; el correo de H61 se arma con estos datos. */
    public record AlertaCreada(Long id, Long usuarioId, Long productoId, BigDecimal precioAnterior,
                               BigDecimal precioNuevo) {
    }

    /**
     * H59: se llama después de guardar un cambio de precio, en la misma transacción (PUT /productos/{id} y carga
     * masiva), así que si algo falla no queda ni el precio ni la alerta. Regla de API.md:
     * - solo si el precio baja y el producto se ve después del cambio (por eso se lee de la vista de búsqueda);
     * - para cada favorito de un usuario activo con notificacionesPrecio, ver {@link #debeAvisar};
     * - al crear la alerta, el precio nuevo queda como último precio avisado de ese favorito.
     */
    @Transactional
    public List<AlertaCreada> registrarBaja(Long productoId, BigDecimal anterior, BigDecimal nuevo) {
        if (anterior == null || nuevo == null || nuevo.compareTo(anterior) >= 0) {
            return List.of();
        }
        List<Interesado> interesados = jdbc.query("select f.usuario_id, f.precio_objetivo, f.ultimo_precio_avisado "
                        + "from favorito f join usuario u on u.id = f.usuario_id "
                        + "join v_producto_busqueda v on v.producto_id = f.producto_id "
                        + "where f.producto_id = :producto and u.activo and u.notificaciones_precio and "
                        + BusquedaService.FILTRO_VISIBLE,
                new MapSqlParameterSource("producto", productoId),
                (rs, i) -> new Interesado(rs.getLong("usuario_id"), rs.getBigDecimal("precio_objetivo"),
                        rs.getBigDecimal("ultimo_precio_avisado")));
        List<AlertaCreada> creadas = new ArrayList<>();
        for (Interesado f : interesados) {
            if (!debeAvisar(nuevo, f.precioObjetivo(), f.ultimoPrecioAvisado())) {
                continue;
            }
            MapSqlParameterSource params = new MapSqlParameterSource("usuario", f.usuarioId())
                    .addValue("producto", productoId)
                    .addValue("anterior", anterior)
                    .addValue("nuevo", nuevo);
            Long id = jdbc.queryForObject("insert into alerta_precio (usuario_id, producto_id, precio_anterior, "
                    + "precio_nuevo) values (:usuario, :producto, :anterior, :nuevo) returning id", params, Long.class);
            jdbc.update("update favorito set ultimo_precio_avisado = :nuevo "
                    + "where usuario_id = :usuario and producto_id = :producto", params);
            creadas.add(new AlertaCreada(id, f.usuarioId(), productoId, anterior, nuevo));
        }
        return creadas;
    }

    /**
     * H59 y H60: sin precio objetivo, hay alerta en cada baja. Con objetivo, solo si el precio nuevo es menor o igual
     * al objetivo y menor que el último precio avisado (o si todavía no se avisó nada).
     */
    static boolean debeAvisar(BigDecimal nuevo, BigDecimal objetivo, BigDecimal ultimoAvisado) {
        if (objetivo == null) {
            return true;
        }
        return nuevo.compareTo(objetivo) <= 0 && (ultimoAvisado == null || nuevo.compareTo(ultimoAvisado) < 0);
    }
}