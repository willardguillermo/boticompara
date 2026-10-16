package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ProductoDetalleResponse;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusquedaServiceTest {

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    BusquedaService service;

    @Captor
    ArgumentCaptor<RowMapper<ProductoDetalleResponse>> mapper;

    private ProductoDetalleResponse ejemplo() {
        return new ProductoDetalleResponse(9L, "Paracetamol Genérico", "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal("2.20"), 90, 2L, "FarmaVida", "Jr. Trujillo 210", "Lurigancho-Chosica",
                "014567890");
    }

    private void vistaDevuelve(List<ProductoDetalleResponse> filas) {
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<ProductoDetalleResponse>>any()))
                .thenReturn(filas);
    }

    @Test
    void detalle_productoVisible_loDevuelve() {
        vistaDevuelve(List.of(ejemplo()));

        ProductoDetalleResponse r = service.detalle(9L);

        assertEquals(9L, r.productoId());
        assertEquals("014567890", r.boticaTelefono());
    }

    @Test
    void detalle_noEstaEnLaVista_responde404() {
        vistaDevuelve(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.detalle(99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Producto no encontrado", ex.getMessage());
    }

    @Test
    void detalle_consultaLaVistaPorId() {
        vistaDevuelve(List.of(ejemplo()));

        service.detalle(9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(),
                ArgumentMatchers.<RowMapper<ProductoDetalleResponse>>any());
        assertTrue(sql.getValue().contains("from v_producto_busqueda where producto_id = :id"));
        assertEquals(9L, params.getValue().getValue("id"));
    }

    @Test
    void detalle_mapeaLasColumnasYTelefonoNulo() throws Exception {
        vistaDevuelve(List.of(ejemplo()));
        service.detalle(9L);
        verify(jdbc).query(anyString(), any(SqlParameterSource.class), mapper.capture());

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("producto_id")).thenReturn(9L);
        when(rs.getString("nombre_comercial")).thenReturn("Paracetamol Genérico");
        when(rs.getString("principio_activo")).thenReturn("Paracetamol");
        when(rs.getString("presentacion")).thenReturn("Tableta 500 mg x 10");
        when(rs.getBigDecimal("precio")).thenReturn(new BigDecimal("2.20"));
        when(rs.getInt("stock")).thenReturn(90);
        when(rs.getLong("botica_id")).thenReturn(2L);
        when(rs.getString("botica_nombre")).thenReturn("FarmaVida");
        when(rs.getString("botica_direccion")).thenReturn("Jr. Trujillo 210");
        when(rs.getString("botica_distrito")).thenReturn("Lurigancho-Chosica");
        // botica_telefono sin stub: el mock devuelve null, como una botica sin teléfono

        ProductoDetalleResponse r = mapper.getValue().mapRow(rs, 0);

        assertEquals(new ProductoDetalleResponse(9L, "Paracetamol Genérico", "Paracetamol",
                "Tableta 500 mg x 10", new BigDecimal("2.20"), 90, 2L, "FarmaVida",
                "Jr. Trujillo 210", "Lurigancho-Chosica", null), r);
    }
}