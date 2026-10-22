package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.AlternativaResponse;
import com.boticompara.usuario.dto.AlternativasResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.AlternativasService.Consultado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H36, H37, H41: la base se simula; ningún test toca Supabase. */
@ExtendWith(MockitoExtension.class)
class AlternativasServiceTest {

    /** Fragmentos que distinguen cada una de las tres consultas. */
    private static final String CONSULTADO = "select v.producto_id, v.precio";
    private static final String GRUPO = "medicamento_grupo g where g.grupo_id = :grupo";
    private static final String PRINCIPIO = "lower(trim(v.principio_activo))";
    private static final String INSERT = "insert into consulta_comparacion (producto_id, usuario_id)";

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    AlternativasService service;

    @Captor
    ArgumentCaptor<RowMapper<Consultado>> mapperConsultado;

    @Captor
    ArgumentCaptor<RowMapper<AlternativaResponse>> mapperAlternativa;

    private static Consultado consultado(Long grupoId) {
        return new Consultado(7L, new BigDecimal("10.00"), "Paracetamol", grupoId);
    }

    private static AlternativaResponse alt(long id, String precio, String tipo) {
        return new AlternativaResponse(id, "Producto " + id, "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal(precio), 10, id, "Botica " + id, "Av. " + id, "Chosica", tipo, null);
    }

    private void consultadoEs(List<Consultado> filas) {
        when(jdbc.query(contains(CONSULTADO), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Consultado>>any())).thenReturn(filas);
    }

    private void delGrupoEs(List<AlternativaResponse> filas) {
        when(jdbc.query(contains(GRUPO), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AlternativaResponse>>any())).thenReturn(filas);
    }

    private void delPrincipioEs(List<AlternativaResponse> filas) {
        when(jdbc.query(contains(PRINCIPIO), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AlternativaResponse>>any())).thenReturn(filas);
    }

    /** Una fila de la vista con el producto 13 (Paracetamol Genérico, FarmaVida, S/ 2.20). */
    private static ResultSet filaDeVista() throws SQLException {
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
        return rs;
    }

    private static AlternativaResponse esperada(String tipo) {
        return new AlternativaResponse(13L, "Paracetamol Genérico", "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal("2.20"), 90, 2L, "FarmaVida", "Jr. Trujillo 210", "Lurigancho-Chosica", tipo, null);
    }

    @Test
    void productoNoVisible_responde404YNoBuscaAlternativas() {
        consultadoEs(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.alternativas(99L, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Producto no encontrado", ex.getMessage());
        verify(jdbc, times(1)).query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Object>>any());
    }

    @Test
    void conGrupo_usaLosProductosDelGrupoComoEquivalentes() {
        consultadoEs(List.of(consultado(5L)));
        delGrupoEs(List.of(alt(13, "2.20", "EQUIVALENTE")));

        AlternativasResponse r = service.alternativas(7L, null);

        assertEquals(7L, r.productoId());
        assertFalse(r.sinEquivalentes());
        assertEquals(1, r.alternativas().size());
        assertEquals("EQUIVALENTE", r.alternativas().get(0).tipoCoincidencia());
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(contains(GRUPO), params.capture(), ArgumentMatchers.<RowMapper<AlternativaResponse>>any());
        assertEquals(7L, params.getValue().getValue("id"));
        assertEquals(5L, params.getValue().getValue("grupo"));
        verify(jdbc, never()).query(contains(PRINCIPIO), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AlternativaResponse>>any());
    }

    @Test
    void sinGrupo_usaElMismoPrincipioActivo() {
        consultadoEs(List.of(consultado(null)));
        delPrincipioEs(List.of(alt(13, "2.20", "PRINCIPIO_ACTIVO")));

        AlternativasResponse r = service.alternativas(7L, null);

        assertEquals("PRINCIPIO_ACTIVO", r.alternativas().get(0).tipoCoincidencia());
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(contains(PRINCIPIO), params.capture(), ArgumentMatchers.<RowMapper<AlternativaResponse>>any());
        assertEquals(7L, params.getValue().getValue("id"));
        assertEquals("Paracetamol", params.getValue().getValue("principio"));
        verify(jdbc, never()).query(contains(GRUPO), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AlternativaResponse>>any());
    }

    @Test
    void calculaElAhorroSoloCuandoLaAlternativaEsMasBarata() {
        consultadoEs(List.of(consultado(5L)));   // el consultado cuesta 10.00
        delGrupoEs(List.of(alt(1, "8.00", "EQUIVALENTE"), alt(2, "10.00", "EQUIVALENTE"),
                alt(3, "12.00", "EQUIVALENTE")));

        List<AlternativaResponse> a = service.alternativas(7L, null).alternativas();

        assertEquals(new BigDecimal("20.0"), a.get(0).ahorroPorcentaje());
        assertNull(a.get(1).ahorroPorcentaje());
        assertNull(a.get(2).ahorroPorcentaje());
    }

    @Test
    void sinAlternativas_marcaSinEquivalentes() {
        consultadoEs(List.of(consultado(null)));
        delPrincipioEs(List.of());

        AlternativasResponse r = service.alternativas(7L, null);

        assertTrue(r.sinEquivalentes());
        assertTrue(r.alternativas().isEmpty());
    }

    @Test
    void lasConsultasExcluyenLoNoVisibleYSeOrdenanPorPrecio() {
        consultadoEs(List.of(consultado(5L)));
        delGrupoEs(List.of());

        service.alternativas(7L, null);

        ArgumentCaptor<String> consultas = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).query(consultas.capture(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Object>>any());
        String sqlConsultado = consultas.getAllValues().get(0);
        String sql = consultas.getAllValues().get(1);

        assertTrue(sqlConsultado.contains("from v_producto_busqueda v where v.producto_id = :id "
                + "and not oculto and registro_vigente is not false"));
        assertTrue(sql.contains("from v_producto_busqueda v where v.producto_id <> :id"));
        assertTrue(sql.contains("and not oculto and registro_vigente is not false"));
        assertTrue(sql.endsWith("order by v.precio asc, v.botica_nombre asc, v.producto_id asc limit 20"));
    }

    @Test
    void mapeaLasColumnasDelGrupoConTipoEquivalente() throws SQLException {
        consultadoEs(List.of(consultado(5L)));
        delGrupoEs(List.of());
        service.alternativas(7L, null);
        verify(jdbc).query(contains(GRUPO), any(SqlParameterSource.class), mapperAlternativa.capture());

        AlternativaResponse r = mapperAlternativa.getValue().mapRow(filaDeVista(), 0);

        assertEquals(esperada("EQUIVALENTE"), r);
    }

    @Test
    void mapeaLasColumnasDelRespaldoConTipoPrincipioActivo() throws SQLException {
        consultadoEs(List.of(consultado(null)));
        delPrincipioEs(List.of());
        service.alternativas(7L, null);
        verify(jdbc).query(contains(PRINCIPIO), any(SqlParameterSource.class), mapperAlternativa.capture());

        AlternativaResponse r = mapperAlternativa.getValue().mapRow(filaDeVista(), 0);

        assertEquals(esperada("PRINCIPIO_ACTIVO"), r);
    }

    @Test
    void mapeaElConsultadoConYSinGrupo() throws SQLException {
        consultadoEs(List.of(consultado(null)));
        delPrincipioEs(List.of());
        service.alternativas(7L, null);
        verify(jdbc).query(contains(CONSULTADO), any(SqlParameterSource.class), mapperConsultado.capture());

        ResultSet sinGrupo = mock(ResultSet.class);
        when(sinGrupo.getLong("producto_id")).thenReturn(7L);
        when(sinGrupo.getBigDecimal("precio")).thenReturn(new BigDecimal("10.00"));
        when(sinGrupo.getString("principio_activo")).thenReturn("Paracetamol");
        when(sinGrupo.getLong("grupo_id")).thenReturn(0L);
        when(sinGrupo.wasNull()).thenReturn(true);

        ResultSet conGrupo = mock(ResultSet.class);
        when(conGrupo.getLong("producto_id")).thenReturn(7L);
        when(conGrupo.getBigDecimal("precio")).thenReturn(new BigDecimal("10.00"));
        when(conGrupo.getString("principio_activo")).thenReturn("Paracetamol");
        when(conGrupo.getLong("grupo_id")).thenReturn(5L);

        assertEquals(consultado(null), mapperConsultado.getValue().mapRow(sinGrupo, 0));
        assertEquals(consultado(5L), mapperConsultado.getValue().mapRow(conGrupo, 0));
    }

    // ---------- H42: registro de la consulta ----------

    @Test
    void registraLaConsultaConElUsuarioDeLaSesion() {
        consultadoEs(List.of(consultado(5L)));
        delGrupoEs(List.of(alt(13, "2.20", "EQUIVALENTE")));

        service.alternativas(7L, 42L);

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains(INSERT), params.capture());
        assertEquals(7L, params.getValue().getValue("producto"));
        assertEquals(42L, params.getValue().getValue("usuario"));
    }

    @Test
    void sinSesion_registraLaConsultaSinUsuario() {
        consultadoEs(List.of(consultado(5L)));
        delGrupoEs(List.of(alt(13, "2.20", "EQUIVALENTE")));

        service.alternativas(7L, null);

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains(INSERT), params.capture());
        assertEquals(7L, params.getValue().getValue("producto"));
        assertNull(params.getValue().getValue("usuario"));
    }

    @Test
    void tambienRegistraLaConsultaCuandoNoHayEquivalentes() {
        consultadoEs(List.of(consultado(null)));
        delPrincipioEs(List.of());

        AlternativasResponse r = service.alternativas(7L, null);

        assertTrue(r.sinEquivalentes());
        verify(jdbc).update(contains(INSERT), any(SqlParameterSource.class));
    }

    @Test
    void productoNoVisible_noRegistraNada() {
        consultadoEs(List.of());

        assertThrows(ApiException.class, () -> service.alternativas(99L, 42L));

        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void siFallaElRegistro_laRespuestaSigueIgual() {
        consultadoEs(List.of(consultado(5L)));
        delGrupoEs(List.of(alt(13, "2.20", "EQUIVALENTE")));
        when(jdbc.update(anyString(), any(SqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("base caida"));

        AlternativasResponse r = service.alternativas(7L, 42L);

        assertEquals(1, r.alternativas().size());
        assertFalse(r.sinEquivalentes());
    }
}
