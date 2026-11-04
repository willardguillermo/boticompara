package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.AlertaResponse;
import com.boticompara.usuario.dto.ResumenAlertasResponse;
import com.boticompara.usuario.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
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
}