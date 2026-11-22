package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.NotificacionResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.NotificacionService.DatosReserva;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** H103: la base se simula; ningún test toca Supabase. */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    private static final long DUENO = 1L;

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    NotificacionService service;

    @Captor
    ArgumentCaptor<RowMapper<NotificacionResponse>> mapper;

    @Captor
    ArgumentCaptor<SqlParameterSource> params;

    @Captor
    ArgumentCaptor<String> sql;

    private String sqlDeListar(String soloNoLeidas) {
        service.listar(DUENO, soloNoLeidas);
        verify(jdbc).query(sql.capture(), params.capture(), ArgumentMatchers.<RowMapper<NotificacionResponse>>any());
        assertEquals(DUENO, params.getValue().getValue("usuario"));
        return sql.getValue();
    }

    private DatosReserva amoxil(String condicionVenta) {
        return new DatosReserva(DUENO, "BC-7K2QXM", "Amoxil", 3, new BigDecimal("24.00"), condicionVenta);
    }

    // ---------- listar ----------

    @Test
    void listar_soloLasDelDuenoDeLaMasRecienteALaMasAntiguaMaximo50() {
        String consulta = sqlDeListar(null);

        assertTrue(consulta.contains("where usuario_id = :usuario order by creado_en desc, id desc limit 50"), consulta);
        assertFalse(consulta.contains("not leida"), consulta);
    }

    @Test
    void listar_soloNoLeidas() {
        assertTrue(sqlDeListar("true").contains("where usuario_id = :usuario and not leida order by"));
    }

    @Test
    void listar_soloNoLeidasFalseEsLoMismoQueNoEnviarlo() {
        assertFalse(sqlDeListar("false").contains("not leida"));
    }

    @Test
    void listar_soloNoLeidasInvalidoDa400SinConsultar() {
        for (String valor : new String[]{"si", "1", ""}) {
            ApiException ex = assertThrows(ApiException.class, () -> service.listar(DUENO, valor));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void listar_devuelveElFormatoDelContrato() throws Exception {
        Instant creado = Instant.parse("2026-11-22T15:30:00.123456Z");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(31L);
        when(rs.getString("tipo")).thenReturn("NUEVA_RESERVA");
        when(rs.getString("titulo")).thenReturn("Nueva reserva BC-7K2QXM");
        when(rs.getString("mensaje")).thenReturn("Amoxil x 3 · S/ 72.00");
        when(rs.getLong("reserva_id")).thenReturn(15L);
        when(rs.getBoolean("leida")).thenReturn(false);
        when(rs.getTimestamp("creado_en")).thenReturn(Timestamp.from(creado));

        service.listar(DUENO, null);
        verify(jdbc).query(anyString(), any(SqlParameterSource.class), mapper.capture());

        assertEquals(new NotificacionResponse(31L, "NUEVA_RESERVA", "Nueva reserva BC-7K2QXM",
                "Amoxil x 3 · S/ 72.00", 15L, false, creado), mapper.getValue().mapRow(rs, 0));
    }

    // ---------- contar ----------

    @Test
    void totalNoLeidas_cuentaTodasLasDelDuenoSinLimite() {
        when(jdbc.queryForObject(sql.capture(), params.capture(), eq(Long.class))).thenReturn(73L);

        assertEquals(73, service.totalNoLeidas(DUENO).total());
        assertEquals("select count(*) from notificacion where usuario_id = :usuario and not leida", sql.getValue());
        assertEquals(DUENO, params.getValue().getValue("usuario"));
    }

    // ---------- marcar ----------

    @Test
    void marcarLeida_soloLaPropia() {
        when(jdbc.update(sql.capture(), params.capture())).thenReturn(1);

        service.marcarLeida(DUENO, 31L);

        // Sin "and not leida": si ya estaba leída también cuenta una fila y responde 204
        assertEquals("update notificacion set leida = true where id = :id and usuario_id = :usuario", sql.getValue());
        assertEquals(31L, params.getValue().getValue("id"));
        assertEquals(DUENO, params.getValue().getValue("usuario"));
    }

    @Test
    void marcarLeida_queNoExisteODeOtroDuenoDaElMismo404() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> service.marcarLeida(DUENO, 99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("La notificación no existe", ex.getMessage());
    }

    @Test
    void marcarTodasLeidas_soloLasDelDuenoYSinErrorSiNoHabia() {
        when(jdbc.update(sql.capture(), params.capture())).thenReturn(0);

        service.marcarTodasLeidas(DUENO);

        assertEquals("update notificacion set leida = true where usuario_id = :usuario and not leida", sql.getValue());
        assertEquals(DUENO, params.getValue().getValue("usuario"));
    }

    // ---------- textos ----------

    @Test
    void titulos_comoElContrato() {
        assertEquals("Nueva reserva BC-7K2QXM", TipoNotificacion.NUEVA_RESERVA.titulo("BC-7K2QXM"));
        assertEquals("Reserva cancelada BC-7K2QXM", TipoNotificacion.RESERVA_CANCELADA.titulo("BC-7K2QXM"));
    }

    @Test
    void mensaje_nuevaReservaConRecetaLoAvisa() {
        assertEquals("Amoxil x 3 · S/ 72.00 · Requiere receta médica",
                NotificacionService.mensaje(TipoNotificacion.NUEVA_RESERVA, amoxil("CON_RECETA")));
    }

    @Test
    void mensaje_nuevaReservaSinRecetaOSinDatoNoAgregaNada() {
        assertEquals("Amoxil x 3 · S/ 72.00",
                NotificacionService.mensaje(TipoNotificacion.NUEVA_RESERVA, amoxil("SIN_RECETA")));
        assertEquals("Amoxil x 3 · S/ 72.00",
                NotificacionService.mensaje(TipoNotificacion.NUEVA_RESERVA, amoxil(null)));
    }

    @Test
    void mensaje_canceladaNuncaAgregaLaReceta() {
        assertEquals("Amoxil x 3 · S/ 72.00",
                NotificacionService.mensaje(TipoNotificacion.RESERVA_CANCELADA, amoxil("CON_RECETA")));
    }

    @Test
    void crear_noLeeNingunDatoDelComprador() {
        when(jdbc.queryForObject(sql.capture(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<DatosReserva>>any())).thenReturn(amoxil(null));

        service.crear(TipoNotificacion.NUEVA_RESERVA, 15L);

        assertFalse(sql.getValue().contains("join usuario"), sql.getValue());
        assertFalse(sql.getValue().contains("r.usuario_id"), sql.getValue());
    }
}
