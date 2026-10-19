package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.dto.ProductoDetalleResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.BusquedaService.FilaBusqueda;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    // ---------- H27: búsqueda por cercanía ----------

    /** Comprador en Chosica. */
    private static final double LAT = -11.9425;
    private static final double LNG = -76.699;

    /** Botica a ~1.09 km (0.01° de longitud). */
    private static FilaBusqueda cerca(long id, String precio) {
        return fila(id, precio, "-11.942500", "-76.689000");
    }

    /** Botica en la Plaza de Armas de Lima, a ~39.14 km. */
    private static FilaBusqueda lejos(long id, String precio) {
        return fila(id, precio, "-12.046374", "-77.042793");
    }

    private static FilaBusqueda sinCoordenadas(long id, String precio) {
        return fila(id, precio, null, null);
    }

    private static FilaBusqueda fila(long id, String precio, String lat, String lng) {
        BusquedaResponse p = new BusquedaResponse(id, "Paracetamol " + id, "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal(precio), 10, id, "Botica " + id, "Av. " + id, "Chosica", null);
        return new FilaBusqueda(p, lat == null ? null : new BigDecimal(lat), lng == null ? null : new BigDecimal(lng));
    }

    /** La base ya devuelve las filas ordenadas por precio ascendente. */
    private void baseDevuelve(FilaBusqueda... filas) {
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<FilaBusqueda>>any()))
                .thenReturn(List.of(filas));
    }

    private static List<Long> ids(List<BusquedaResponse> r) {
        return r.stream().map(BusquedaResponse::productoId).toList();
    }

    @Test
    void buscar_sinUbicacion_funcionaComoAntes() {
        baseDevuelve(lejos(1, "2.20"), sinCoordenadas(2, "2.50"), cerca(3, "3.00"));

        List<BusquedaResponse> r = service.buscar("para", "nombre", null, null, null, null);

        assertEquals(List.of(1L, 2L, 3L), ids(r));
        assertTrue(r.stream().allMatch(x -> x.distanciaKm() == null));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<FilaBusqueda>>any());
        assertTrue(sql.getValue().contains("order by precio asc"));
    }

    @Test
    void buscar_conUbicacion_ordenaPorDistanciaYSinCoordenadasAlFinal() {
        baseDevuelve(lejos(1, "2.20"), sinCoordenadas(2, "2.50"), cerca(3, "3.00"));

        List<BusquedaResponse> r = service.buscar("para", "nombre", null, LAT, LNG, null);

        assertEquals(List.of(3L, 1L, 2L), ids(r));
        assertEquals(new BigDecimal("1.09"), r.get(0).distanciaKm());
        assertEquals(new BigDecimal("39.14"), r.get(1).distanciaKm());
        assertNull(r.get(2).distanciaKm());
    }

    @Test
    void buscar_conUbicacionYOrdenPrecio_mantieneElPrecioYTraeLaDistancia() {
        baseDevuelve(lejos(1, "2.20"), sinCoordenadas(2, "2.50"), cerca(3, "3.00"));

        List<BusquedaResponse> r = service.buscar("para", "nombre", "precio_asc", LAT, LNG, null);

        assertEquals(List.of(1L, 2L, 3L), ids(r));
        assertEquals(new BigDecimal("39.14"), r.get(0).distanciaKm());
    }

    @Test
    void buscar_aIgualDistancia_primeroElMasBarato() {
        baseDevuelve(cerca(1, "1.00"), cerca(2, "3.00"));

        List<BusquedaResponse> r = service.buscar("para", "nombre", "distancia", LAT, LNG, null);

        assertEquals(List.of(1L, 2L), ids(r));
    }

    @Test
    void buscar_radioVacio_usa5Km() {
        baseDevuelve(lejos(1, "2.20"), sinCoordenadas(2, "2.50"), cerca(3, "3.00"));

        List<BusquedaResponse> r = service.buscar("para", "nombre", null, LAT, LNG, "");

        assertEquals(List.of(3L), ids(r));
    }

    @Test
    void buscar_conRadio_dejaFueraLasBoticasSinCoordenadas() {
        baseDevuelve(lejos(1, "2.20"), sinCoordenadas(2, "2.50"), cerca(3, "3.00"));

        List<BusquedaResponse> r = service.buscar("para", "nombre", null, LAT, LNG, "50");

        assertEquals(List.of(3L, 1L), ids(r));
    }

    private void esperar400(String mensaje, Runnable llamada) {
        ApiException ex = assertThrows(ApiException.class, llamada::run);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(mensaje, ex.getMessage());
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<FilaBusqueda>>any());
    }

    @Test
    void buscar_soloLatitud_responde400() {
        esperar400("Debes enviar lat y lng juntas",
                () -> service.buscar("para", "nombre", null, LAT, null, null));
    }

    @Test
    void buscar_latitudFueraDeRango_responde400() {
        esperar400("La latitud debe estar entre -90 y 90",
                () -> service.buscar("para", "nombre", null, 91.0, LNG, null));
    }

    @Test
    void buscar_radioSinUbicacion_responde400() {
        esperar400("El radio requiere lat y lng",
                () -> service.buscar("para", "nombre", null, null, null, "5"));
    }

    @Test
    void buscar_radioInvalido_responde400() {
        esperar400("radioKm debe ser un número mayor que 0 y hasta 50",
                () -> service.buscar("para", "nombre", null, LAT, LNG, "51"));
        esperar400("radioKm debe ser un número mayor que 0 y hasta 50",
                () -> service.buscar("para", "nombre", null, LAT, LNG, "abc"));
        esperar400("radioKm debe ser un número mayor que 0 y hasta 50",
                () -> service.buscar("para", "nombre", null, LAT, LNG, "0"));
    }

    @Test
    void buscar_ordenPorDistanciaSinUbicacion_responde400() {
        esperar400("El orden por distancia requiere lat y lng",
                () -> service.buscar("para", "nombre", "distancia", null, null, null));
    }

    @Test
    void buscar_ordenInvalido_responde400() {
        esperar400("El orden debe ser precio_asc, precio_desc o distancia",
                () -> service.buscar("para", "nombre", "cerca", null, null, null));
    }
}