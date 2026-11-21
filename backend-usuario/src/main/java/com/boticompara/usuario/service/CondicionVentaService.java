package com.boticompara.usuario.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * H72: condición de venta (DIGEMID) del medicamento enlazado a un producto (columna medicamento.condicion_venta).
 * SIN_RECETA, CON_RECETA, RECETA_RETENIDA o null (producto sin enlazar o sin dato).
 * La usan las reservas y los pedidos, que leen el producto de su tabla y no de la vista de búsqueda.
 * Se lee con JDBC, como la búsqueda: no hay entidad Medicamento en este backend.
 */
@Service
@RequiredArgsConstructor
public class CondicionVentaService {

    public static final String RECETA_RETENIDA = "RECETA_RETENIDA";

    private final NamedParameterJdbcTemplate jdbc;

    /** La condición de un medicamento; null si medicamentoId es null o no tiene el dato. */
    public String deMedicamento(Long medicamentoId) {
        if (medicamentoId == null) {
            return null;
        }
        return deMedicamentos(List.of(medicamentoId)).get(medicamentoId);
    }

    /** Condición por id de medicamento, en una sola consulta. Los ids sin dato no aparecen en el mapa. */
    public Map<Long, String> deMedicamentos(Collection<Long> medicamentoIds) {
        List<Long> ids = medicamentoIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> resultado = new HashMap<>();
        if (ids.isEmpty()) {
            return resultado;
        }
        jdbc.query("select id, condicion_venta from medicamento where id in (:ids) and condicion_venta is not null",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    resultado.put(rs.getLong("id"), rs.getString("condicion_venta"));
                });
        return resultado;
    }
}
