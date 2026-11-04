package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.AlertaResponse;
import com.boticompara.usuario.error.ApiException;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H59: la base se simula; ningún test toca Supabase. */
@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    private static final Instant FECHA = Instant.parse("2026-11-05T14:20:00Z");

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    AlertaService service;

    @Captor
    ArgumentCaptor<RowMapper<AlertaResponse>> mapper;

    private String sqlDeListar(String soloNoLeidas) {
        service.listar(42L, soloNoLeidas);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), ArgumentMatchers.<RowMapper<AlertaResponse>>any());
        assertEquals(42L, params.getValue().getValue("usuario"));
        return sql.getValue();
    }

    @Test
    void listar_todas_delUsuarioDeLaMasRecienteALaMasAntiguaMaximo100() {
        String sql = sqlDeListar(null);

        assertTrue(sql.contains("where a.usuario_id = :usuario order by a.fecha desc, a.id desc limit 100"));
        assertFalse(sql.contains("not a.leida"));
    }

    @Test
    void listar_soloNoLeidas_filtraLasLeidas() {
        String sql = sqlDeListar("true");

        assertTrue(sql.contains("where a.usuario_id = :usuario and not a.leida order by"));
    }

    @Test
    void listar_soloNoLeidasFalse_lasTraeTodas() {
        assertFalse(sqlDeListar("false").contains("not a.leida"));
    }

    @Test
    void listar_soloNoLeidasInvalido_responde400SinConsultar() {
        ApiException ex = assertThrows(ApiException.class, () -> service.listar(42L, "si"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("soloNoLeidas debe ser true o false", ex.getMessage());
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AlertaResponse>>any());
    }

    @Test
    void listar_leeElProductoYLaBoticaAunqueYaNoSeVean() {
        String sql = sqlDeListar(null);

        assertTrue(sql.contains("from alerta_precio a join producto p on p.id = a.producto_id "
                + "join botica b on b.id = p.botica_id"));
        assertFalse(sql.contains("v_producto_busqueda"));
    }

    @Test
    void listar_mapeaLasColumnasYCalculaElAhorro() throws Exception {
        service.listar(42L, null);
        verify(jdbc).query(anyString(), any(SqlParameterSource.class), mapper.capture());

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(31L);
        when(rs.getLong("producto_id")).thenReturn(14L);
        when(rs.getString("nombre_comercial")).thenReturn("Panadol");
        when(rs.getString("presentacion")).thenReturn("Tableta 500 mg x 10");
        when(rs.getString("botica_nombre")).thenReturn("FarmaVida");
        when(rs.getBigDecimal("precio_anterior")).thenReturn(new BigDecimal("7.90"));
        when(rs.getBigDecimal("precio_nuevo")).thenReturn(new BigDecimal("7.50"));
        when(rs.getBoolean("leida")).thenReturn(false);
        when(rs.getTimestamp("fecha")).thenReturn(Timestamp.from(FECHA));

        assertEquals(new AlertaResponse(31L, 14L, "Panadol", "Tableta 500 mg x 10", "FarmaVida",
                        new BigDecimal("7.90"), new BigDecimal("7.50"), new BigDecimal("0.40"), false, FECHA),
                mapper.getValue().mapRow(rs, 0));
    }

    @Test
    void resumen_cuentaLasNoLeidasDelUsuario() {
        when(jdbc.queryForObject(contains("select count(*) from alerta_precio where usuario_id = :usuario and not leida"),
                any(SqlParameterSource.class), eq(Long.class))).thenReturn(2L);

        assertEquals(2L, service.resumen(42L).noLeidas());
    }

    @Test
    void marcarLeida_deSuAlerta_actualizaFiltrandoPorUsuario() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        service.marcarLeida(42L, 31L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertEquals("update alerta_precio set leida = true where id = :id and usuario_id = :usuario", sql.getValue());
        assertEquals(31L, params.getValue().getValue("id"));
        assertEquals(42L, params.getValue().getValue("usuario"));
    }

    @Test
    void marcarLeida_inexistenteODeOtroUsuario_responde404() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> service.marcarLeida(42L, 999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Alerta no encontrada", ex.getMessage());
    }

    @Test
    void marcarTodasLeidas_soloLasDelUsuario() {
        service.marcarTodasLeidas(42L);

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(eq("update alerta_precio set leida = true where usuario_id = :usuario and not leida"),
                params.capture());
        assertEquals(42L, params.getValue().getValue("usuario"));
    }
}