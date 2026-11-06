package com.boticompara.usuario.service;

import com.boticompara.usuario.service.AlertaService.AlertaCreada;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * H61: correo de baja de precio. Se ejecuta después del commit y en otro hilo, así que no hace más lenta la
 * respuesta y no sale ningún correo si la transacción se deshace. Un correo por usuario con todas sus bajas.
 * Si el correo falla, la alerta ya quedó guardada: solo se deja un aviso en el log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaCorreoService {

    private static final String SQL_AVISOS = "select a.usuario_id, u.nombre, u.correo, p.nombre_comercial as producto, "
            + "p.presentacion, b.nombre_comercial as botica, a.precio_anterior, a.precio_nuevo "
            + "from alerta_precio a join usuario u on u.id = a.usuario_id join producto p on p.id = a.producto_id "
            + "join botica b on b.id = p.botica_id where a.id in (:ids) order by a.usuario_id, a.id";

    private final NamedParameterJdbcTemplate jdbc;
    private final CorreoService correo;

    record AvisoBaja(Long usuarioId, String nombre, String correo, String producto, String presentacion,
                     String botica, BigDecimal anterior, BigDecimal nuevo) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCrearseAlertas(AlertasCreadas evento) {
        try {
            enviar(cargarAvisos(evento.alertas()));
        } catch (RuntimeException e) {
            log.error("No se pudo preparar el correo de baja de precio: {}", e.getMessage());
        }
    }

    List<AvisoBaja> cargarAvisos(List<AlertaCreada> alertas) {
        if (alertas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = alertas.stream().map(AlertaCreada::id).toList();
        return jdbc.query(SQL_AVISOS, new MapSqlParameterSource("ids", ids),
                (rs, i) -> new AvisoBaja(rs.getLong("usuario_id"), rs.getString("nombre"), rs.getString("correo"),
                        rs.getString("producto"), rs.getString("presentacion"), rs.getString("botica"),
                        rs.getBigDecimal("precio_anterior"), rs.getBigDecimal("precio_nuevo")));
    }

    /** @return cantidad de correos que el servidor aceptó */
    int enviar(List<AvisoBaja> avisos) {
        Map<Long, List<AvisoBaja>> porUsuario = avisos.stream()
                .collect(Collectors.groupingBy(AvisoBaja::usuarioId, LinkedHashMap::new, Collectors.toList()));
        int enviados = 0;
        for (List<AvisoBaja> bajas : porUsuario.values()) {
            if (correo.enviar(bajas.get(0).correo(), asunto(bajas), cuerpo(bajas))) {
                enviados++;
            }
        }
        log.info("Correo de baja de precio: {} enviado(s)", enviados);
        return enviados;
    }

    static String asunto(List<AvisoBaja> bajas) {
        if (bajas.size() == 1) {
            AvisoBaja b = bajas.get(0);
            return b.producto() + " bajó a " + soles(b.nuevo()) + " en " + b.botica();
        }
        return "Bajaron de precio " + bajas.size() + " productos de tus favoritos";
    }

    static String cuerpo(List<AvisoBaja> bajas) {
        StringBuilder sb = new StringBuilder("Hola ").append(bajas.get(0).nombre()).append(",\n\n");
        sb.append(bajas.size() == 1 ? "Un producto de tus favoritos bajó de precio:\n\n"
                : "Estos productos de tus favoritos bajaron de precio:\n\n");
        for (AvisoBaja b : bajas) {
            sb.append("- ").append(b.producto()).append(" (").append(b.presentacion()).append(") en ")
                    .append(b.botica()).append(": de ").append(soles(b.anterior()))
                    .append(" a ").append(soles(b.nuevo())).append("\n");
        }
        sb.append("\nPuedes verlos en la pantalla Alertas de la app.\n\nBotiCompara\n");
        return sb.toString();
    }

    private static String soles(BigDecimal valor) {
        return "S/ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}