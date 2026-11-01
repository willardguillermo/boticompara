package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.FavoritoResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.FavoritoService.Agregado;
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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H43 y H44: la base se simula; ningún test toca Supabase. */
@ExtendWith(MockitoExtension.class)
class FavoritoServiceTest {

    private static final String INSERT = "insert into favorito (usuario_id, producto_id)";
    private static final String UNO = "and f.producto_id = :producto";
    private static final String LISTA = "order by f.fecha desc, f.producto_id desc";
    private static final Instant FECHA = Instant.parse("2026-10-22T23:00:00Z");

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    FavoritoService service;

    @Captor
    ArgumentCaptor<RowMapper<FavoritoResponse>> mapper;

    private static FavoritoResponse fav(long id) {
        return new FavoritoResponse(id, "Producto " + id, "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal("2.20"), 90, 2L, "FarmaVida", "Jr. Trujillo 210", "Lurigancho-Chosica", FECHA);
    }

    private void insertaFilas(int filas) {
        when(jdbc.update(contains(INSERT), any(SqlParameterSource.class))).thenReturn(filas);
    }

    private void productoGuardadoEs(List<FavoritoResponse> filas) {
        when(jdbc.query(contains(UNO), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<FavoritoResponse>>any())).thenReturn(filas);
    }

    @Test
    void agregar_productoNuevo_loGuardaYIndicaQueSeCreo() {
        insertaFilas(1);
        productoGuardadoEs(List.of(fav(9)));

        Agregado r = service.agregar(42L, 9L);

        assertTrue(r.creado());
        assertEquals(fav(9), r.favorito());
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains(INSERT), params.capture());
        assertEquals(42L, params.getValue().getValue("usuario"));
        assertEquals(9L, params.getValue().getValue("producto"));
    }

    @Test
    void agregar_yaGuardado_noLoDuplicaYIndicaQueNoSeCreo() {
        insertaFilas(0);
        productoGuardadoEs(List.of(fav(9)));

        Agregado r = service.agregar(42L, 9L);

        assertFalse(r.creado());
        assertEquals(fav(9), r.favorito());
    }

    @Test
    void agregar_productoNoVisible_responde404() {
        insertaFilas(0);
        productoGuardadoEs(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.agregar(42L, 999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Producto no encontrado", ex.getMessage());
    }

    @Test
    void agregar_usaLaVistaConLosFiltrosDeLaBusqueda() {
        insertaFilas(1);
        productoGuardadoEs(List.of(fav(9)));

        service.agregar(42L, 9L);

        ArgumentCaptor<String> insert = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(insert.capture(), any(SqlParameterSource.class));
        assertTrue(insert.getValue().contains("from v_producto_busqueda v where v.producto_id = :producto "
                + "and not oculto and registro_vigente is not false on conflict (usuario_id, producto_id) do nothing"));

        ArgumentCaptor<String> select = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(select.capture(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<FavoritoResponse>>any());
        assertTrue(select.getValue().contains("from favorito f join v_producto_busqueda v on v.producto_id = f.producto_id "
                + "where f.usuario_id = :usuario and not oculto and registro_vigente is not false "
                + "and f.producto_id = :producto"));
    }

    @Test
    void quitar_borraLaFilaDelUsuario() {
        service.quitar(42L, 9L);

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("delete from favorito where usuario_id = :usuario and producto_id = :producto"),
                params.capture());
        assertEquals(42L, params.getValue().getValue("usuario"));
        assertEquals(9L, params.getValue().getValue("producto"));
    }

    @Test
    void quitarTodos_borraTodosLosFavoritosDelUsuario() {
        service.quitarTodos(42L);

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("delete from favorito where usuario_id = :usuario"), params.capture());
        assertEquals(42L, params.getValue().getValue("usuario"));
    }

    @Test
    void quitar_siNoEstaba_noFalla() {
        assertDoesNotThrow(() -> service.quitar(42L, 999L));
    }

    @Test
    void listar_devuelveLosFavoritosDelUsuarioDelMasRecienteAlMasAntiguo() {
        when(jdbc.query(contains(LISTA), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<FavoritoResponse>>any())).thenReturn(List.of(fav(2), fav(1)));

        List<FavoritoResponse> r = service.listar(42L);

        assertEquals(List.of(fav(2), fav(1)), r);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), ArgumentMatchers.<RowMapper<FavoritoResponse>>any());
        assertTrue(sql.getValue().contains("where f.usuario_id = :usuario and not oculto "
                + "and registro_vigente is not false order by f.fecha desc, f.producto_id desc"));
        assertEquals(42L, params.getValue().getValue("usuario"));
    }

    @Test
    void mapeaLasColumnasDeLaVista() throws SQLException {
        service.listar(42L);
        verify(jdbc).query(contains(LISTA), any(SqlParameterSource.class), mapper.capture());

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("producto_id")).thenReturn(13L);
        when(rs.getString("nombre_comercial")).thenReturn("Paracetamol Genérico");
        when(rs.getString("principio_activo")).thenReturn("Paracetamol");
        when(rs.getString("presentacion")).thenReturn("Tableta 500 mg x 10");
        when(rs.getBigDecimal("precio")).thenReturn(new BigDecimal("2.20"));
        when(rs.getInt("stock")).thenReturn(90);
        when(rs.getLong("botica_id")).thenReturn(2L);
        when(rs.getString("botica_nombre")).thenReturn("FarmaVida");
        when(rs.getString("botica_direccion")).thenReturn("Jr. Trujillo 210");
        when(rs.getString("botica_distrito")).thenReturn("Lurigancho-Chosica");
        when(rs.getTimestamp("fecha")).thenReturn(Timestamp.from(FECHA));

        assertEquals(new FavoritoResponse(13L, "Paracetamol Genérico", "Paracetamol", "Tableta 500 mg x 10",
                        new BigDecimal("2.20"), 90, 2L, "FarmaVida", "Jr. Trujillo 210", "Lurigancho-Chosica", FECHA),
                mapper.getValue().mapRow(rs, 0));
    }
}