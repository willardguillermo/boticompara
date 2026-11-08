package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.MedicamentoResponse;
import com.boticompara.usuario.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * H31: catálogo maestro (tablas medicamento y principio_activo, que administra Django).
 * Se lee con JDBC, sin entidades JPA, para no atar el arranque de la API a esas tablas.
 */
@Service
@RequiredArgsConstructor
public class MedicamentoService {

    static final int MAX_RESULTADOS = 20;

    private final NamedParameterJdbcTemplate jdbc;

    /** Selector de la web: busca por nombre comercial o principio activo, sin los ocultos. */
    @Transactional(readOnly = true)
    public List<MedicamentoResponse> buscar(String q) {
        if (q == null || q.trim().length() < 2) {
            throw ApiException.badRequest("La búsqueda requiere mínimo 2 caracteres");
        }
        String sql = "select m.id, m.nombre_comercial, pa.nombre as principio_activo, m.presentacion, m.concentracion "
                + "from medicamento m join principio_activo pa on pa.id = m.principio_activo_id "
                + "where not m.oculto "
                + "and (lower(m.nombre_comercial) like :patron or lower(pa.nombre) like :patron) "
                + "order by m.nombre_comercial, m.presentacion, m.id "
                + "limit " + MAX_RESULTADOS;
        String patron = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return jdbc.query(sql, new MapSqlParameterSource("patron", patron), (rs, i) -> new MedicamentoResponse(
                rs.getLong("id"),
                rs.getString("nombre_comercial"),
                rs.getString("principio_activo"),
                rs.getString("presentacion"),
                rs.getString("concentracion")));
    }
    /** Medicamento que se va a enlazar: si está oculto y el nombre de su principio activo. */
    record Enlazable(boolean oculto, String principioActivo) {
    }

    /**
     * Para enlazar un producto (H31): 400 si el medicamento no existe o está oculto.
     * D2: devuelve el nombre de su principio activo, que se copia al producto.
     */
    /** D4: resultado de evaluar un medicamento para enlazar: o su principio activo, o el motivo del rechazo. */
    public record EnlaceMedicamento(String principioActivo, String error) {
    }

    /**
     * D4 (carga masiva): igual que principioActivoEnlazable, pero no lanza excepción: devuelve el motivo. Así una fila
     * con un medicamento inválido no marca la transacción de la carga como rollback-only.
     */
    @Transactional(readOnly = true)
    public EnlaceMedicamento enlace(Long id) {
        List<Enlazable> filas = enlazable(id);
        if (filas.isEmpty()) {
            return new EnlaceMedicamento(null, "El medicamento no existe en el catálogo");
        }
        if (filas.get(0).oculto()) {
            return new EnlaceMedicamento(null, "El medicamento no está disponible en el catálogo");
        }
        return new EnlaceMedicamento(filas.get(0).principioActivo(), null);
    }

    /**
     * Para enlazar un producto (H31): 400 si el medicamento no existe o está oculto.
     * D2: devuelve el nombre de su principio activo, que se copia al producto.
     */
    @Transactional(readOnly = true)
    public String principioActivoEnlazable(Long id) {
        EnlaceMedicamento e = enlace(id);
        if (e.error() != null) {
            throw ApiException.badRequest(e.error());
        }
        return e.principioActivo();
    }

    /**
     * D2: principio activo del medicamento con el que el producto YA está enlazado. No exige que esté visible:
     * si después lo ocultaron, el dueño igual puede editar su producto. null si el medicamento ya no existe.
     */
    @Transactional(readOnly = true)
    public String principioActivoDe(Long id) {
        List<Enlazable> filas = enlazable(id);
        return filas.isEmpty() ? null : filas.get(0).principioActivo();
    }

    private List<Enlazable> enlazable(Long id) {
        return jdbc.query("select m.oculto, pa.nombre as principio_activo from medicamento m "
                        + "join principio_activo pa on pa.id = m.principio_activo_id where m.id = :id",
                new MapSqlParameterSource("id", id),
                (rs, i) -> new Enlazable(rs.getBoolean("oculto"), rs.getString("principio_activo")));
    }
}