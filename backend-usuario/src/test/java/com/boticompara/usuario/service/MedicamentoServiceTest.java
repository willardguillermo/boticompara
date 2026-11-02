package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.MedicamentoResponse;
import com.boticompara.usuario.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
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

    private void enlazableEs(List<MedicamentoService.Enlazable> filas) {
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<MedicamentoService.Enlazable>>any())).thenReturn(filas);
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
    void principioActivoEnlazable_medicamentoVisible_devuelveSuPrincipioActivo() {
        enlazableEs(List.of(new MedicamentoService.Enlazable(false, "Paracetamol")));

        assertEquals("Paracetamol", service.principioActivoEnlazable(12L));
    }

    @Test
    void principioActivoEnlazable_noExiste_responde400() {
        enlazableEs(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.principioActivoEnlazable(99L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("El medicamento no existe en el catálogo", ex.getMessage());
    }

    @Test
    void principioActivoEnlazable_oculto_responde400() {
        enlazableEs(List.of(new MedicamentoService.Enlazable(true, "Paracetamol")));

        ApiException ex = assertThrows(ApiException.class, () -> service.principioActivoEnlazable(12L));

        assertEquals("El medicamento no está disponible en el catálogo", ex.getMessage());
    }

    @Test
    void principioActivoEnlazable_leeElNombreDelPrincipioActivo() {
        enlazableEs(List.of(new MedicamentoService.Enlazable(false, "Paracetamol")));

        service.principioActivoEnlazable(12L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<MedicamentoService.Enlazable>>any());
        assertTrue(sql.getValue().contains("join principio_activo pa on pa.id = m.principio_activo_id"));
    }

    @Test
    void principioActivoDe_medicamentoOculto_igualDevuelveSuPrincipioActivo() {
        enlazableEs(List.of(new MedicamentoService.Enlazable(true, "Paracetamol")));

        assertEquals("Paracetamol", service.principioActivoDe(12L));
    }

    @Test
    void principioActivoDe_noExiste_devuelveNull() {
        enlazableEs(List.of());

        assertNull(service.principioActivoDe(99L));
    }
}
