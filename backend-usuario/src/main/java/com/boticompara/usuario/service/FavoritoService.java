package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.FavoritoResponse;
import com.boticompara.usuario.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * H43 y H44: favoritos del comprador. Se leen de la misma vista y con los mismos filtros que la búsqueda,
 * así que un producto que deja de verse (sin stock, oculto, con registro vencido...) no sale en la lista,
 * pero su fila en favorito se conserva y reaparece si vuelve a estar disponible.
 */
@Service
@RequiredArgsConstructor
public class FavoritoService {

    private static final String SELECT = "select v.producto_id, v.nombre_comercial, v.principio_activo, "
            + "v.presentacion, v.precio, v.stock, v.botica_id, v.botica_nombre, v.botica_direccion, "
            + "v.botica_distrito, f.fecha "
            + "from favorito f join v_producto_busqueda v on v.producto_id = f.producto_id "
            + "where f.usuario_id = :usuario and " + BusquedaService.FILTRO_VISIBLE;

    private final NamedParameterJdbcTemplate jdbc;

    /** creado es false si el producto ya estaba en los favoritos. */
    public record Agregado(FavoritoResponse favorito, boolean creado) {
    }

    @Transactional
    public Agregado agregar(Long usuarioId, Long productoId) {
        // Solo se guarda si el producto se ve hoy; si ya estaba guardado, no hace nada
        int nuevos = jdbc.update("insert into favorito (usuario_id, producto_id) "
                        + "select cast(:usuario as bigint), v.producto_id from v_producto_busqueda v "
                        + "where v.producto_id = :producto and " + BusquedaService.FILTRO_VISIBLE + " "
                        + "on conflict (usuario_id, producto_id) do nothing",
                parametros(usuarioId, productoId));
        List<FavoritoResponse> filas = jdbc.query(SELECT + " and f.producto_id = :producto",
                parametros(usuarioId, productoId), (rs, i) -> favorito(rs));
        if (filas.isEmpty()) {
            throw ApiException.notFound("Producto no encontrado");
        }
        return new Agregado(filas.get(0), nuevos > 0);
    }

    @Transactional(readOnly = true)
    public List<FavoritoResponse> listar(Long usuarioId) {
        return jdbc.query(SELECT + " order by f.fecha desc, f.producto_id desc",
                new MapSqlParameterSource("usuario", usuarioId), (rs, i) -> favorito(rs));
    }

    /** Quitar es idempotente: si el producto no estaba en los favoritos, no pasa nada. */
    @Transactional
    public void quitar(Long usuarioId, Long productoId) {
        jdbc.update("delete from favorito where usuario_id = :usuario and producto_id = :producto",
                parametros(usuarioId, productoId));
    }

    /** D1: al eliminar la cuenta (H19) se borran todos sus favoritos. */
    @Transactional
    public void quitarTodos(Long usuarioId) {
        jdbc.update("delete from favorito where usuario_id = :usuario", new MapSqlParameterSource("usuario", usuarioId));
    }

    private static MapSqlParameterSource parametros(Long usuarioId, Long productoId) {
        return new MapSqlParameterSource("usuario", usuarioId).addValue("producto", productoId);
    }

    private static FavoritoResponse favorito(ResultSet rs) throws SQLException {
        return new FavoritoResponse(
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
                rs.getTimestamp("fecha").toInstant());
    }
}