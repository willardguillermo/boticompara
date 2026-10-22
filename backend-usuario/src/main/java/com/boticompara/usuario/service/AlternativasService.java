package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.AlternativaResponse;
import com.boticompara.usuario.dto.AlternativasResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.util.Ahorro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * H36, H37, H38, H41: alternativas de un producto para comparar precios.
 * Todo sale de la vista v_producto_busqueda, así que ya excluye lo que el comprador no puede ver:
 * productos inactivos o sin stock (H38), de boticas no aprobadas, o con medicamento oculto o
 * registro vencido (H39). Se lee con JDBC, igual que la búsqueda.
 * H42: cada consulta de un producto visible se registra en consulta_comparacion. No hay @Transactional a
 * propósito: el insert es independiente de las lecturas y, si falla, no debe afectar la respuesta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlternativasService {

    static final int MAX_ALTERNATIVAS = 20;
    static final String EQUIVALENTE = "EQUIVALENTE";
    static final String PRINCIPIO_ACTIVO = "PRINCIPIO_ACTIVO";

    private static final String COLUMNAS = "v.producto_id, v.nombre_comercial, v.principio_activo, "
            + "v.presentacion, v.precio, v.stock, v.botica_id, v.botica_nombre, v.botica_direccion, "
            + "v.botica_distrito";
    private static final String ORDEN = " order by v.precio asc, v.botica_nombre asc, v.producto_id asc limit "
            + MAX_ALTERNATIVAS;

    private final NamedParameterJdbcTemplate jdbc;

    /** El producto consultado: su precio, su principio activo y su grupo de equivalencia (null si no tiene). */
    record Consultado(Long productoId, BigDecimal precio, String principioActivo, Long grupoId) {
    }

    /**
     * Si el medicamento del producto (H31) está en un grupo de equivalencia, las alternativas son los productos
     * de ese grupo (EQUIVALENTE). Si el producto no está enlazado, o su medicamento no está en ningún grupo,
     * se usa el mismo principio activo, sin distinguir mayúsculas ni espacios de más (PRINCIPIO_ACTIVO).
     */
    public AlternativasResponse alternativas(Long id, Long usuarioId) {
        Consultado consultado = consultado(id);
        List<AlternativaResponse> filas = consultado.grupoId() != null
                ? delGrupo(consultado)
                : delMismoPrincipio(consultado);
        List<AlternativaResponse> alternativas = filas.stream()
                .map(a -> a.conAhorro(Ahorro.porcentaje(consultado.precio(), a.precio())))
                .toList();
        AlternativasResponse respuesta = new AlternativasResponse(id, alternativas.isEmpty(), alternativas);
        registrarConsulta(id, usuarioId);
        return respuesta;
    }

    /**
     * H42: guarda la consulta con el usuario de la sesión (null si no hay sesión). Solo se llama para productos
     * visibles, así que las consultas con 404 no se registran. Si falla, solo queda un aviso en el log.
     */
    private void registrarConsulta(Long productoId, Long usuarioId) {
        try {
            jdbc.update("insert into consulta_comparacion (producto_id, usuario_id) values (:producto, :usuario)",
                    new MapSqlParameterSource()
                            .addValue("producto", productoId, Types.BIGINT)
                            .addValue("usuario", usuarioId, Types.BIGINT));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la consulta de alternativas del producto {}: {}", productoId, e.toString());
        }
    }

    private Consultado consultado(Long id) {
        String sql = "select v.producto_id, v.precio, v.principio_activo, "
                + "(select g.grupo_id from medicamento_grupo g where g.medicamento_id = v.medicamento_id) as grupo_id "
                + "from v_producto_busqueda v where v.producto_id = :id and " + BusquedaService.FILTRO_VISIBLE;
        List<Consultado> filas = jdbc.query(sql, new MapSqlParameterSource("id", id), (rs, i) -> {
            long grupo = rs.getLong("grupo_id");
            Long grupoId = rs.wasNull() ? null : grupo;
            return new Consultado(rs.getLong("producto_id"), rs.getBigDecimal("precio"),
                    rs.getString("principio_activo"), grupoId);
        });
        if (filas.isEmpty()) {
            throw ApiException.notFound("Producto no encontrado");
        }
        return filas.get(0);
    }

    private List<AlternativaResponse> delGrupo(Consultado c) {
        String sql = "select " + COLUMNAS + " from v_producto_busqueda v "
                + "where v.producto_id <> :id "
                + "and v.medicamento_id in (select g.medicamento_id from medicamento_grupo g where g.grupo_id = :grupo) "
                + "and " + BusquedaService.FILTRO_VISIBLE + ORDEN;
        MapSqlParameterSource params = new MapSqlParameterSource("id", c.productoId()).addValue("grupo", c.grupoId());
        return jdbc.query(sql, params, (rs, i) -> alternativa(rs, EQUIVALENTE));
    }

    private List<AlternativaResponse> delMismoPrincipio(Consultado c) {
        String sql = "select " + COLUMNAS + " from v_producto_busqueda v "
                + "where v.producto_id <> :id "
                + "and lower(trim(v.principio_activo)) = lower(trim(cast(:principio as text))) "
                + "and " + BusquedaService.FILTRO_VISIBLE + ORDEN;
        MapSqlParameterSource params = new MapSqlParameterSource("id", c.productoId())
                .addValue("principio", c.principioActivo());
        return jdbc.query(sql, params, (rs, i) -> alternativa(rs, PRINCIPIO_ACTIVO));
    }

    private static AlternativaResponse alternativa(ResultSet rs, String tipo) throws SQLException {
        return new AlternativaResponse(
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
                tipo,
                null);
    }
}
