package com.boticompara.usuario.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroBusquedaServiceTest {

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    RegistroBusquedaService service;

    private SqlParameterSource registrado() {
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(anyString(), params.capture());
        return params.getValue();
    }

    @Test
    void registrar_guardaElTextoNormalizadoTipoResultadosYUsuario() {
        service.registrar("  ParaCETAMOL ", "principio", 4, 7L);

        SqlParameterSource p = registrado();
        assertEquals("paracetamol", p.getValue("texto"));
        assertEquals("principio", p.getValue("tipo"));
        assertEquals(4, p.getValue("resultados"));
        assertEquals(7L, p.getValue("usuario"));
    }

    @Test
    void registrar_sinSesion_guardaSinUsuario() {
        service.registrar("panadol", "nombre", 0, null);

        assertNull(registrado().getValue("usuario"));
    }

    @Test
    void registrar_textoLargo_seRecortaAlLargoDeLaColumna() {
        service.registrar("a".repeat(200), "nombre", 0, 7L);

        assertEquals(RegistroBusquedaService.MAX_TEXTO, ((String) registrado().getValue("texto")).length());
    }

    @Test
    void registrar_noGuardaLaUbicacionDelComprador() {
        service.registrar("panadol", "nombre", 1, 7L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(SqlParameterSource.class));
        assertEquals("insert into busqueda (usuario_id, texto, tipo, resultados) "
                + "values (:usuario, :texto, :tipo, :resultados)", sql.getValue());
        assertFalse(sql.getValue().contains("lat"));
    }

    @Test
    void registrar_siLaBaseFalla_noLanzaError() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("base caida"));

        assertDoesNotThrow(() -> service.registrar("panadol", "nombre", 1, 7L));
    }
}