package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.NombreCorto;
import com.boticompara.usuario.entity.EstadoReserva;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** H82-H83: correos de pedidos. Corren después del commit y en otro hilo, como las alertas de precio (H61). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoCorreoService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String SQL_DUENO = "select u.correo, u.nombre as dueno, r.codigo, p.nombre_comercial as producto, "
            + "p.presentacion, r.cantidad, r.precio_unitario, r.vence_en, r.creado_en, cu.nombre as comprador "
            + "from reserva r join producto p on p.id = r.producto_id join botica b on b.id = p.botica_id "
            + "join usuario u on u.id = b.usuario_id join usuario cu on cu.id = r.usuario_id where r.id = :id";

    private static final String SQL_COMPRADOR = "select u.correo, u.nombre as comprador, r.codigo, r.estado, p.nombre_comercial as producto, "
            + "p.presentacion, r.cantidad, r.precio_unitario, r.vence_en, r.motivo_rechazo, b.nombre_comercial as botica, "
            + "b.direccion, b.distrito, b.telefono from reserva r join producto p on p.id = r.producto_id "
            + "join botica b on b.id = p.botica_id join usuario u on u.id = r.usuario_id where r.id = :id";

    private final NamedParameterJdbcTemplate jdbc;
    private final CorreoService correo;


    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCrearseReserva(ReservaCreada evento) {
        try {
            enviarNuevaReserva(evento.reservaId());
        } catch (RuntimeException e) {
            log.error("No se pudo preparar el correo de nueva reserva: {}", e.getMessage());
        }
    }

    void enviarNuevaReserva(Long reservaId) {
        jdbc.query(SQL_DUENO, new MapSqlParameterSource("id", reservaId), rs -> {
            DatosNuevaReserva d = new DatosNuevaReserva(rs.getString("correo"), rs.getString("dueno"),
                    rs.getString("codigo"), rs.getString("producto"), rs.getString("presentacion"),
                    rs.getInt("cantidad"), rs.getBigDecimal("precio_unitario"), rs.getString("comprador"),
                    rs.getTimestamp("vence_en").toInstant(), rs.getTimestamp("creado_en").toInstant());
            correo.enviar(d.correo(), asuntoNuevaReserva(d), cuerpoNuevaReserva(d));
        });
    }

    record DatosNuevaReserva(String correo, String dueno, String codigo, String producto, String presentacion,
                             int cantidad, BigDecimal precioUnitario, String comprador, Instant venceEn,
                             Instant creadoEn) {
    }

    static String cuerpoNuevaReserva(DatosNuevaReserva d) {
        BigDecimal total = d.precioUnitario().multiply(BigDecimal.valueOf(d.cantidad()))
                .setScale(2, RoundingMode.HALF_UP);
        return "Hola " + d.dueno() + ",\n\n"
                + "Llegó una reserva a tu botica:\n\n"
                + "Código: " + d.codigo() + "\n"
                + "Producto: " + d.producto() + " (" + d.presentacion() + ")\n"
                + "Cantidad: " + d.cantidad() + "\n"
                + "Precio unitario: S/ " + d.precioUnitario().setScale(2, RoundingMode.HALF_UP).toPlainString() + "\n"
                + "Total: S/ " + total.toPlainString() + "\n"
                + "Comprador: " + NombreCorto.de(d.comprador()) + "\n"
                + "Fecha de la reserva: " + FECHA.format(d.creadoEn().atZone(LIMA)) + "\n"
                + "Vence el: " + FECHA.format(d.venceEn().atZone(LIMA)) + "\n\n"
                + "Puedes atenderla desde la sección Pedidos de la web.\n\nBotiCompara\n";
    }

    static String asuntoNuevaReserva(DatosNuevaReserva d) {
        return "Nueva reserva " + d.codigo() + ": " + d.producto() + " x " + d.cantidad();
    }



    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCambiarEstado(EstadoPedidoCambiado evento) {
        try {
            enviarCambioDeEstado(evento.reservaId(), evento.estado());
        } catch (RuntimeException e) {
            log.error("No se pudo preparar el correo de cambio de estado: {}", e.getMessage());
        }
    }

    void enviarCambioDeEstado(Long reservaId, EstadoReserva estado) {
        jdbc.query(SQL_COMPRADOR, new MapSqlParameterSource("id", reservaId), rs -> {
            DatosCambioEstado d = new DatosCambioEstado(rs.getString("correo"), rs.getString("comprador"),
                    rs.getString("codigo"), EstadoReserva.valueOf(rs.getString("estado")),
                    rs.getString("producto"), rs.getString("presentacion"), rs.getInt("cantidad"),
                    rs.getBigDecimal("precio_unitario"), rs.getTimestamp("vence_en").toInstant(),
                    rs.getString("motivo_rechazo"), rs.getString("botica"), rs.getString("direccion"),
                    rs.getString("distrito"), rs.getString("telefono"));
            correo.enviar(d.correo(), asuntoCambioEstado(d), cuerpoCambioEstado(d));
        });
    }

    record DatosCambioEstado(String correo, String comprador, String codigo, EstadoReserva estado,
                             String producto, String presentacion, int cantidad, BigDecimal precioUnitario,
                             Instant venceEn, String motivoRechazo, String botica, String direccion,
                             String distrito, String telefono) {
    }

    static String asuntoCambioEstado(DatosCambioEstado d) {
        return d.estado() == EstadoReserva.LISTA
                ? "Tu reserva " + d.codigo() + " está lista para recoger"
                : "Tu reserva " + d.codigo() + " fue rechazada";
    }

    static String cuerpoCambioEstado(DatosCambioEstado d) {
        BigDecimal total = d.precioUnitario().multiply(BigDecimal.valueOf(d.cantidad()))
                .setScale(2, RoundingMode.HALF_UP);
        StringBuilder sb = new StringBuilder("Hola ").append(d.comprador()).append(",\n\n");
        if (d.estado() == EstadoReserva.LISTA) {
            sb.append("Tu reserva ya está lista para recoger en la botica.\n\n");
        } else {
            sb.append("Lamentablemente la botica no pudo atender tu reserva.\n\n");
        }
        sb.append("Código: ").append(d.codigo()).append("\n")
                .append("Producto: ").append(d.producto()).append(" (").append(d.presentacion()).append(")\n")
                .append("Cantidad: ").append(d.cantidad()).append("\n")
                .append("Total: S/ ").append(total.toPlainString()).append("\n")
                .append("Botica: ").append(d.botica()).append(" — ").append(d.direccion())
                .append(", ").append(d.distrito()).append("\n")
                .append("Teléfono: ").append(d.telefono() == null ? "no registrado" : d.telefono()).append("\n");
        if (d.estado() == EstadoReserva.LISTA) {
            sb.append("Puedes recogerla hasta el ")
                    .append(FECHA.format(d.venceEn().atZone(LIMA))).append(".\n");
        } else {
            sb.append("Motivo: ").append(d.motivoRechazo()).append("\n");
        }
        return sb.append("\nBotiCompara\n").toString();
    }
}