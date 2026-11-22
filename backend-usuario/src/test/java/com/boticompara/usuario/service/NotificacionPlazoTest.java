package com.boticompara.usuario.service;

import com.boticompara.usuario.BaseDePrueba;
import com.boticompara.usuario.dto.NotificacionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * H103: el plazo de 7 días lo evalúa PostgreSQL, con las consultas reales del servicio. Las filas son inventadas: van
 * en un "with notificacion as (values ...)" que tapa a la tabla, así que no lee ni escribe notificaciones de verdad.
 * La transacción es de solo lectura en la propia base. Sin base configurada, la clase se omite.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
@Transactional(readOnly = true)
class NotificacionPlazoTest {

    /** Un id que ningún usuario tiene. */
    private static final long DUENO = -1L;

    private static final String FILAS = "with notificacion (id, usuario_id, tipo, titulo, mensaje, reserva_id, leida, "
            + "creado_en) as (values "
            + fila(1, DUENO, false, "8 days") + ", "
            + fila(2, DUENO, true, "8 days") + ", "
            + fila(3, DUENO, false, "7 days 1 minute") + ", "
            + fila(4, DUENO, false, "6 days 23 hours 59 minutes") + ", "
            + fila(5, DUENO, false, "6 days") + ", "
            + fila(6, DUENO, true, "1 hour") + ", "
            + fila(7, -2L, false, "1 hour") + ") ";

    @Autowired
    JdbcTemplate base;

    NotificacionService service;

    NotificacionPlazoTest() {
        BaseDePrueba.exigir();
    }

    @BeforeEach
    void preparar() {
        service = new NotificacionService(new NamedParameterJdbcTemplate(base) {
            @Override
            protected PreparedStatementCreator getPreparedStatementCreator(String sql, SqlParameterSource params) {
                return super.getPreparedStatementCreator(FILAS + sql, params);
            }
        });
    }

    private static String fila(long id, long usuario, boolean leida, String antiguedad) {
        return "(" + id + "::bigint, " + usuario + "::bigint, 'NUEVA_RESERVA', 'titulo', 'mensaje', 15::bigint, "
                + leida + ", now() - interval '" + antiguedad + "')";
    }

    private List<Long> ids(String soloNoLeidas) {
        return service.listar(DUENO, soloNoLeidas).stream().map(NotificacionResponse::id).toList();
    }

    @Test
    void laBaseRechazaCualquierEscrituraDeEstaPrueba() {
        assertEquals("on", base.queryForObject("show transaction_read_only", String.class));
    }

    @Test
    void laDeHace8DiasNoSeListaYLaDeHace6Si() {
        assertEquals(List.of(6L, 5L, 4L), ids(null));
    }

    @Test
    void soloNoLeidasRespetaElMismoPlazo() {
        assertEquals(List.of(5L, 4L), ids("true"));
    }

    @Test
    void elContadorUsaElMismoPlazoQueLaLista() {
        assertEquals(2, service.totalNoLeidas(DUENO).total());
    }
}
