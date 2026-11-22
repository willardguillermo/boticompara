package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.NotificacionResponse;
import com.boticompara.usuario.dto.TotalNoLeidasResponse;
import com.boticompara.usuario.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** H103: notificaciones del dueño (API.md, sección 12; tabla notificacion, con JDBC como las alertas de H59). */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    static final int MAX_NOTIFICACIONES = 50;

    /** Son avisos del momento: pasado el plazo no se listan ni se cuentan (el pedido sigue en Pedidos). */
    private static final String EN_PLAZO = "creado_en > now() - interval '7 days'";

    private static final String CON_RECETA = "CON_RECETA";

    /** No pasa por la tabla usuario: ningún dato del comprador llega al aviso. */
    private static final String SQL_DATOS = "select b.usuario_id as dueno_id, r.codigo, p.nombre_comercial, r.cantidad, "
            + "r.precio_unitario, m.condicion_venta "
            + "from reserva r join producto p on p.id = r.producto_id join botica b on b.id = p.botica_id "
            + "left join medicamento m on m.id = p.medicamento_id where r.id = :reserva";

    private final NamedParameterJdbcTemplate jdbc;

    /** soloNoLeidas: null (por defecto false), "true" o "false"; cualquier otro valor responde 400. */
    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(Long usuarioId, String soloNoLeidas) {
        String sql = "select id, tipo, titulo, mensaje, reserva_id, leida, creado_en from notificacion "
                + "where usuario_id = :usuario and " + EN_PLAZO + " "
                + (soloNoLeidas(soloNoLeidas) ? "and not leida " : "")
                + "order by creado_en desc, id desc limit " + MAX_NOTIFICACIONES;
        return jdbc.query(sql, new MapSqlParameterSource("usuario", usuarioId), (rs, i) -> new NotificacionResponse(
                rs.getLong("id"),
                rs.getString("tipo"),
                rs.getString("titulo"),
                rs.getString("mensaje"),
                rs.getLong("reserva_id"),
                rs.getBoolean("leida"),
                rs.getTimestamp("creado_en").toInstant()));
    }

    @Transactional(readOnly = true)
    public TotalNoLeidasResponse totalNoLeidas(Long usuarioId) {
        Long total = jdbc.queryForObject(
                "select count(*) from notificacion where usuario_id = :usuario and not leida and " + EN_PLAZO,
                new MapSqlParameterSource("usuario", usuarioId), Long.class);
        return new TotalNoLeidasResponse(total == null ? 0 : total);
    }

    /** Idempotente: si ya estaba leída no pasa nada. Mismo 404 si no existe o es de otro usuario. */
    @Transactional
    public void marcarLeida(Long usuarioId, Long notificacionId) {
        int filas = jdbc.update("update notificacion set leida = true where id = :id and usuario_id = :usuario",
                new MapSqlParameterSource("id", notificacionId).addValue("usuario", usuarioId));
        if (filas == 0) {
            throw ApiException.notFound("La notificación no existe");
        }
    }

    @Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        jdbc.update("update notificacion set leida = true where usuario_id = :usuario and not leida",
                new MapSqlParameterSource("usuario", usuarioId));
    }

    /**
     * Guarda el aviso para el dueño de la botica del producto, en una transacción nueva: la de la reserva ya hizo
     * commit. Si ya había uno de la misma reserva y tipo, el índice único lanza DuplicateKeyException.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void crear(TipoNotificacion tipo, Long reservaId) {
        DatosReserva d = jdbc.queryForObject(SQL_DATOS, new MapSqlParameterSource("reserva", reservaId),
                (rs, i) -> new DatosReserva(rs.getLong("dueno_id"), rs.getString("codigo"),
                        rs.getString("nombre_comercial"), rs.getInt("cantidad"),
                        rs.getBigDecimal("precio_unitario"), rs.getString("condicion_venta")));
        jdbc.update("insert into notificacion (usuario_id, tipo, titulo, mensaje, reserva_id) "
                        + "values (:dueno, :tipo, :titulo, :mensaje, :reserva)",
                new MapSqlParameterSource("dueno", d.duenoId())
                        .addValue("tipo", tipo.name())
                        .addValue("titulo", tipo.titulo(d.codigo()))
                        .addValue("mensaje", mensaje(tipo, d))
                        .addValue("reserva", reservaId));
    }

    record DatosReserva(Long duenoId, String codigo, String producto, int cantidad, BigDecimal precioUnitario,
                        String condicionVenta) {
    }

    /** "Amoxil x 3 · S/ 72.00"; en NUEVA_RESERVA de un producto CON_RECETA agrega "· Requiere receta médica". */
    static String mensaje(TipoNotificacion tipo, DatosReserva d) {
        BigDecimal total = d.precioUnitario().multiply(BigDecimal.valueOf(d.cantidad()))
                .setScale(2, RoundingMode.HALF_UP);
        String mensaje = d.producto() + " x " + d.cantidad() + " · S/ " + total.toPlainString();
        boolean pideReceta = tipo == TipoNotificacion.NUEVA_RESERVA && CON_RECETA.equals(d.condicionVenta());
        return pideReceta ? mensaje + " · Requiere receta médica" : mensaje;
    }

    private static boolean soloNoLeidas(String valor) {
        if (valor == null) {
            return false;
        }
        return switch (valor) {
            case "true" -> true;
            case "false" -> false;
            default -> throw ApiException.badRequest("soloNoLeidas debe ser true o false");
        };
    }
}
