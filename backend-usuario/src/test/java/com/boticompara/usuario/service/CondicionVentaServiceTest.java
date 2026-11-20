package com.boticompara.usuario.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** H72: lectura de medicamento.condicion_venta para reservas y pedidos. */
@ExtendWith(MockitoExtension.class)
class CondicionVentaServiceTest {

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    CondicionVentaService service;

    @Test
    void productoSinEnlazarNoConsultaLaBase() {
        assertNull(service.deMedicamento(null));
        assertTrue(service.deMedicamentos(Arrays.asList(null, null)).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test
    void leeLaCondicionDeCadaMedicamentoEnUnaSolaConsulta() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(40L);
        when(rs.getString("condicion_venta")).thenReturn("RECETA_RETENIDA");
        doAnswer(inv -> {
            inv.<RowCallbackHandler>getArgument(2).processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));

        Map<Long, String> r = service.deMedicamentos(Arrays.asList(40L, null, 40L));

        assertEquals(Map.of(40L, "RECETA_RETENIDA"), r);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), params.capture(), any(RowCallbackHandler.class));
        assertEquals(List.of(40L), params.getValue().getValue("ids"));
    }

    @Test
    void medicamentoSinCondicionDevuelveNull() {
        assertNull(service.deMedicamento(41L));
    }
}
