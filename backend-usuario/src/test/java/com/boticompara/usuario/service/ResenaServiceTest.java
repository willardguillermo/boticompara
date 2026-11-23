package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ResenaDtos;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Resena;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ReservaRepository;
import com.boticompara.usuario.repository.ResenaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResenaServiceTest {

    private static final long COMPRADOR = 5L;
    private static final long BOTICA = 2L;

    @Mock BoticaRepository boticas;
    @Mock ReservaRepository reservas;
    @Mock ResenaRepository resenas;
    @Mock UsuarioRepository usuarios;

    ResenaService service;

    @BeforeEach
    void preparar() {
        service = new ResenaService(boticas, reservas, resenas, usuarios);
    }

    private void botica(String estado) {
        Botica b = new Botica();
        b.setId(BOTICA);
        b.setEstado(estado);
        when(boticas.findById(BOTICA)).thenReturn(Optional.of(b));
    }

    private Resena resena(long id, short estrellas, String comentario, String respuesta) {
        Resena r = new Resena();
        r.setId(id);
        r.setBoticaId(BOTICA);
        r.setUsuarioId(COMPRADOR);
        r.setEstrellas(estrellas);
        r.setComentario(comentario);
        r.setRespuesta(respuesta);
        r.setRespondidaEn(respuesta == null ? null : Instant.now());
        r.setCreadoEn(Instant.now());
        r.setActualizadoEn(Instant.now());
        return r;
    }

    /** Lo que devuelve la consulta de resumen: suma de estrellas y total de las no ocultas. */
    private void resumen(long suma, long total) {
        when(resenas.resumen(BOTICA)).thenReturn(List.<Object[]>of(new Object[] {suma, total}));
    }

    private void pagina(List<Resena> items, int numero, long total) {
        when(resenas.findByBoticaIdAndOcultaFalse(any(), any()))
                .thenReturn(new PageImpl<>(items, PageRequest.of(numero, 10), total));
    }

    // ---------- Guardar (H85, H86) ----------

    @Test
    void estrellasFueraDeRangoDan400() {
        botica("APROBADO");

        ApiException cero = assertThrows(ApiException.class, () -> service.guardar(COMPRADOR, BOTICA, 0, null));
        ApiException seis = assertThrows(ApiException.class, () -> service.guardar(COMPRADOR, BOTICA, 6, null));

        assertEquals(HttpStatus.BAD_REQUEST, cero.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, seis.getStatus());
        verify(resenas, never()).saveAndFlush(any(Resena.class));
    }

    @Test
    void estrellasNulasDan400() {
        botica("APROBADO");

        ApiException ex = assertThrows(ApiException.class, () -> service.guardar(COMPRADOR, BOTICA, null, "Bien"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void comentarioDe501CaracteresDa400() {
        botica("APROBADO");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.guardar(COMPRADOR, BOTICA, 4, "x".repeat(501)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void comentarioDe500CaracteresSeAcepta() {
        botica("APROBADO");
        when(reservas.hayEntregadaDe(COMPRADOR, BOTICA)).thenReturn(true);
        when(resenas.findByUsuarioIdAndBoticaId(COMPRADOR, BOTICA)).thenReturn(Optional.empty());

        ResenaService.Guardada g = service.guardar(COMPRADOR, BOTICA, 3, "x".repeat(500));

        assertTrue(g.creada());
        assertEquals(500, g.resena().comentario().length());
    }

    @Test
    void sinReservaEntregadaDa403() {
        botica("APROBADO");
        when(reservas.hayEntregadaDe(COMPRADOR, BOTICA)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.guardar(COMPRADOR, BOTICA, 4, "Bien"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Solo puedes reseñar boticas donde ya recogiste una reserva", ex.getMessage());
        verify(resenas, never()).saveAndFlush(any(Resena.class));
    }

    @Test
    void boticaNoAprobadaDa404() {
        botica("PENDIENTE");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.guardar(COMPRADOR, BOTICA, 4, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void primeraVezCreaYDevuelveLaResenaConElComentarioSinEspacios() {
        botica("APROBADO");
        when(reservas.hayEntregadaDe(COMPRADOR, BOTICA)).thenReturn(true);
        when(resenas.findByUsuarioIdAndBoticaId(COMPRADOR, BOTICA)).thenReturn(Optional.empty());

        ResenaService.Guardada g = service.guardar(COMPRADOR, BOTICA, 5, "  Muy buena atención  ");

        assertTrue(g.creada());
        ArgumentCaptor<Resena> guardada = ArgumentCaptor.forClass(Resena.class);
        verify(resenas).saveAndFlush(guardada.capture());
        assertEquals(COMPRADOR, guardada.getValue().getUsuarioId());
        assertEquals(BOTICA, guardada.getValue().getBoticaId());
        assertEquals("Muy buena atención", guardada.getValue().getComentario());
        assertEquals((short) 5, g.resena().estrellas());
        assertEquals("Muy buena atención", g.resena().comentario());
        assertFalse(g.resena().oculta());
    }

    @Test
    void segundaVezActualizaSinTocarLaRespuestaDelDueno() {
        botica("APROBADO");
        when(reservas.hayEntregadaDe(COMPRADOR, BOTICA)).thenReturn(true);
        Resena existente = resena(8L, (short) 4, "Bien", "¡Gracias!");
        when(resenas.findByUsuarioIdAndBoticaId(COMPRADOR, BOTICA)).thenReturn(Optional.of(existente));

        ResenaService.Guardada g = service.guardar(COMPRADOR, BOTICA, 2, "  ");

        assertFalse(g.creada());
        assertEquals((short) 2, existente.getEstrellas());
        assertNull(existente.getComentario());
        assertEquals("¡Gracias!", existente.getRespuesta());
        assertEquals(8L, g.resena().id());
        assertEquals("¡Gracias!", g.resena().respuesta());
    }

    @Test
    void editarNoQuitaElOcultamientoDelAdministrador() {
        botica("APROBADO");
        when(reservas.hayEntregadaDe(COMPRADOR, BOTICA)).thenReturn(true);
        Resena existente = resena(8L, (short) 4, "Bien", null);
        existente.setOculta(true);
        when(resenas.findByUsuarioIdAndBoticaId(COMPRADOR, BOTICA)).thenReturn(Optional.of(existente));

        ResenaService.Guardada g = service.guardar(COMPRADOR, BOTICA, 5, "Mejor");

        assertTrue(existente.isOculta());
        assertTrue(g.resena().oculta());
    }

    // ---------- Listado (H87) ----------

    @Test
    void listaPublicaConPromedioDeUnDecimalYSinMiResenaSiNoHayUsuario() {
        botica("APROBADO");
        resumen(13, 3);
        pagina(List.of(resena(8L, (short) 4, "Me atendieron rápido", "¡Gracias, Carlos!")), 0, 1);
        Usuario autor = new Usuario();
        autor.setId(COMPRADOR);
        autor.setNombre("Carlos Ramírez");
        when(usuarios.findAllById(any())).thenReturn(List.of(autor));

        ResenaDtos.Listado l = service.listar(BOTICA, 0, null);

        assertEquals(new BigDecimal("4.3"), l.promedio());
        assertEquals(3L, l.total());
        assertEquals("Carlos R.", l.resenas().get(0).autor());
        assertNull(l.miResena());
        verify(resenas, never()).findByUsuarioIdAndBoticaId(anyLong(), anyLong());
    }

    @Test
    void sinResenasElPromedioEsNulo() {
        botica("APROBADO");
        resumen(0, 0);
        pagina(List.of(), 0, 0);

        ResenaDtos.Listado l = service.listar(BOTICA, 0, null);

        assertNull(l.promedio());
        assertEquals(0L, l.total());
        assertTrue(l.resenas().isEmpty());
    }

    @Test
    void elPromedioRedondeaLaMitadHaciaArriba() {
        botica("APROBADO");
        resumen(23, 20);   // 1.15 exacto: debe dar 1.2
        pagina(List.of(), 0, 0);

        assertEquals(new BigDecimal("1.2"), service.listar(BOTICA, 0, null).promedio());
    }

    @Test
    void conUsuarioConResenaDevuelveMiResenaConSuEstadoOculta() {
        botica("APROBADO");
        resumen(0, 0);
        pagina(List.of(), 0, 0);
        Resena mia = resena(8L, (short) 4, "Bien", null);
        mia.setOculta(true);
        when(resenas.findByUsuarioIdAndBoticaId(COMPRADOR, BOTICA)).thenReturn(Optional.of(mia));

        ResenaDtos.Listado l = service.listar(BOTICA, 0, COMPRADOR);

        assertEquals(8L, l.miResena().id());
        assertTrue(l.miResena().oculta());
    }

    @Test
    void laCuentaEliminadaSaleComoUsuarioEliminado() {
        botica("APROBADO");
        resumen(8, 2);
        Resena deAnonimo = resena(8L, (short) 4, "Bien", null);
        Resena deSinFila = resena(9L, (short) 4, "Bien", null);
        deSinFila.setUsuarioId(99L);
        pagina(List.of(deAnonimo, deSinFila), 0, 2);
        Usuario anonimo = new Usuario();
        anonimo.setId(COMPRADOR);
        anonimo.setNombre("Usuario eliminado");
        when(usuarios.findAllById(any())).thenReturn(List.of(anonimo));

        ResenaDtos.Listado l = service.listar(BOTICA, 0, null);

        assertEquals("Usuario eliminado", l.resenas().get(0).autor());
        assertEquals("Usuario eliminado", l.resenas().get(1).autor());
    }

    @Test
    void pideLaPaginaDeDiezOrdenadaPorFechaDescendente() {
        botica("APROBADO");
        resumen(55, 11);
        pagina(List.of(resena(8L, (short) 5, "Bien", null)), 1, 11);

        ResenaDtos.Listado l = service.listar(BOTICA, 1, null);

        ArgumentCaptor<Pageable> pedida = ArgumentCaptor.forClass(Pageable.class);
        verify(resenas).findByBoticaIdAndOcultaFalse(any(), pedida.capture());
        assertEquals(1, pedida.getValue().getPageNumber());
        assertEquals(10, pedida.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, pedida.getValue().getSort().getOrderFor("creadoEn").getDirection());
        assertEquals(1, l.page());
        assertEquals(2, l.totalPages());
    }

    @Test
    void listarDeUnaBoticaNoAprobadaDa404() {
        botica("PENDIENTE");

        ApiException ex = assertThrows(ApiException.class, () -> service.listar(BOTICA, 0, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(resenas, never()).resumen(anyLong());
    }

    @Test
    void listarDeUnaBoticaInexistenteDa404() {
        when(boticas.findById(BOTICA)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.listar(BOTICA, 0, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void paginaNegativaDa400() {
        ApiException ex = assertThrows(ApiException.class, () -> service.listar(BOTICA, -1, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(boticas);
        verify(resenas, never()).resumen(anyLong());
    }
}
