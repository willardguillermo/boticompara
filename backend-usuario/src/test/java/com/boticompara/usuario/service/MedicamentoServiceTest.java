package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.MedicamentoResponse;
import com.boticompara.usuario.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H31: catálogo maestro. La base se simula: ningún test toca Supabase. */
@ExtendWith(MockitoExtension.class)
class MedicamentoServiceTest {

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    MedicamentoService service;

    private void ocultoEs(List<Boolean> filas) {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Boolean>>any()))
                .thenReturn(filas);
    }

    @Test
    void buscar_textoCorto_responde400SinConsultar() {
        ApiException ex = assertThrows(ApiException.class, () -> service.buscar(" a "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("La búsqueda requiere mínimo 2 caracteres", ex.getMessage());
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<MedicamentoResponse>>any());
    }

    @Test
    void buscar_excluyeOcultosLimitaA20YBuscaEnNombreYPrincipio() {
        MedicamentoResponse panadol = new MedicamentoResponse(12L, "Panadol", "Paracetamol", "Tableta", "500 mg");
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<MedicamentoResponse>>any())).thenReturn(List.of(panadol));

        List<MedicamentoResponse> r = service.buscar("  PARACE ");

        assertEquals(List.of(panadol), r);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), ArgumentMatchers.<RowMapper<MedicamentoResponse>>any());
        assertTrue(sql.getValue().contains("where not m.oculto"));
        assertTrue(sql.getValue().contains("lower(m.nombre_comercial) like :patron or lower(pa.nombre) like :patron"));
        assertTrue(sql.getValue().endsWith("limit 20"));
        assertEquals("%parace%", params.getValue().getValue("patron"));
    }

    @Test
    void validarEnlazable_medicamentoVisible_noLanza() {
        ocultoEs(List.of(false));

        assertDoesNotThrow(() -> service.validarEnlazable(12L));
    }

    @Test
    void validarEnlazable_noExiste_responde400() {
        ocultoEs(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.validarEnlazable(99L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("El medicamento no existe en el catálogo", ex.getMessage());
    }

    @Test
    void validarEnlazable_oculto_responde400() {
        ocultoEs(List.of(true));

        ApiException ex = assertThrows(ApiException.class, () -> service.validarEnlazable(12L));

        assertEquals("El medicamento no está disponible en el catálogo", ex.getMessage());
    }
}
