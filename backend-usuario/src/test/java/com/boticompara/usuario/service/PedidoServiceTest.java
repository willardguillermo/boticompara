package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.PedidoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Reserva;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.ReservaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    private static final long DUENO = 10L;
    private static final long BOTICA = 2L;
    private static final long PRODUCTO = 9L;
    private static final long COMPRADOR = 5L;
    private static final long ID = 15L;

    @Mock BoticaRepository boticas;
    @Mock ProductoRepository productos;
    @Mock ReservaRepository reservas;
    @Mock UsuarioRepository usuarios;
    @Mock CondicionVentaService condiciones;

    PedidoService service;

    @BeforeEach
    void preparar() {
        service = new PedidoService(boticas, productos, reservas, usuarios, condiciones);
    }

    private Botica botica() {
        Botica b = new Botica();
        b.setId(BOTICA);
        return b;
    }

    private Producto producto(int stock, boolean activo) {
        Producto p = new Producto();
        p.setId(PRODUCTO);
        p.setBoticaId(BOTICA);
        p.setNombreComercial("Paracetamol Genérico");
        p.setPresentacion("Tableta 500 mg x 10");
        p.setPrecio(new BigDecimal("2.20"));
        p.setStock(stock);
        p.setActivo(activo);
        return p;
    }

    private Reserva reserva(EstadoReserva estado, Instant venceEn) {
        Reserva r = new Reserva();
        r.setId(ID);
        r.setCodigo("BC-7K2QXM");
        r.setUsuarioId(COMPRADOR);
        r.setProductoId(PRODUCTO);
        r.setCantidad(2);
        r.setPrecioUnitario(new BigDecimal("2.20"));
        r.setEstado(estado);
        r.setVenceEn(venceEn);
        return r;
    }

    private Instant dentroDeUnDia() {
        return Instant.now().plus(1, ChronoUnit.DAYS);
    }

    /** Deja que el servicio encuentre el pedido como si fuera de la botica del dueño. */
    private void pedidoDeMiBotica(Reserva r) {
        when(boticas.findByUsuarioId(DUENO)).thenReturn(Optional.of(botica()));
        when(reservas.bloquearPedidoDeBotica(ID, BOTICA)).thenReturn(Optional.of(r));
    }

    private void guardaLoQueRecibe() {
        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- Listado (H77, H81) ----------

    @Test
    void listarSinEstadoUsaLaConsultaGeneralDeLaBotica() {
        when(boticas.findByUsuarioId(DUENO)).thenReturn(Optional.of(botica()));
        when(reservas.pedidosDeBotica(eq(BOTICA), any(Pageable.class))).thenReturn(List.of());

        assertTrue(service.listar(DUENO, null).isEmpty());
        verify(reservas).pedidosDeBotica(eq(BOTICA), any(Pageable.class));
    }

    @Test
    void listarConEstadoUsaLaConsultaFiltrada() {
        when(boticas.findByUsuarioId(DUENO)).thenReturn(Optional.of(botica()));
        when(reservas.pedidosDeBoticaConEstado(eq(BOTICA), eq(EstadoReserva.LISTA), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(service.listar(DUENO, "LISTA").isEmpty());
        verify(reservas).pedidosDeBoticaConEstado(eq(BOTICA), eq(EstadoReserva.LISTA), any(Pageable.class));
    }

    @Test
    void duenoSinBoticaRecibe404() {
        when(boticas.findByUsuarioId(DUENO)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.listar(DUENO, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(reservas);
    }

    // ---------- Cambio de estado (H79) ----------

    @Test
    void confirmarConStockSuficienteCambiaAConfirmada() {
        Reserva r = reserva(EstadoReserva.PENDIENTE, dentroDeUnDia());
        pedidoDeMiBotica(r);
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(10, true)));
        when(reservas.unidadesApartadasSinEsta(anyLong(), any(), anyLong())).thenReturn(0L);
        guardaLoQueRecibe();

        PedidoResponse resp = service.cambiarEstado(DUENO, ID, "CONFIRMADA");

        assertEquals(EstadoReserva.CONFIRMADA, resp.estado());
    }

    @Test
    void confirmarSinStockSuficienteDa409YNoGuarda() {
        // stock 3, otra reserva activa de 2: disponible 1, el pedido es de 2
        pedidoDeMiBotica(reserva(EstadoReserva.PENDIENTE, dentroDeUnDia()));
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(3, true)));
        when(reservas.unidadesApartadasSinEsta(anyLong(), any(), anyLong())).thenReturn(2L);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.cambiarEstado(DUENO, ID, "CONFIRMADA"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("No hay stock suficiente para aceptar este pedido: disponible 1, pedido 2. Recházalo con un motivo.",
                ex.getMessage());
        verify(reservas, never()).save(any(Reserva.class));
    }

    @Test
    void confirmarUnProductoDadoDeBajaDa409ConElMensajeDelContrato() {
        pedidoDeMiBotica(reserva(EstadoReserva.PENDIENTE, dentroDeUnDia()));
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(10, false)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.cambiarEstado(DUENO, ID, "CONFIRMADA"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Este producto ya no está disponible en tu catálogo. Rechaza el pedido con un motivo.",
                ex.getMessage());
    }

    @Test
    void pasarAListaRenuevaElPlazoDe48HorasYReiniciaElRecordatorio() {
        Reserva r = reserva(EstadoReserva.CONFIRMADA, dentroDeUnDia());
        r.setRecordatorioEnviado(true);
        pedidoDeMiBotica(r);
        guardaLoQueRecibe();

        PedidoResponse resp = service.cambiarEstado(DUENO, ID, "LISTA");

        assertEquals(EstadoReserva.LISTA, resp.estado());
        assertTrue(resp.venceEn().isAfter(Instant.now().plus(47, ChronoUnit.HOURS)));
        assertTrue(resp.venceEn().isBefore(Instant.now().plus(49, ChronoUnit.HOURS)));
        assertFalse(r.isRecordatorioEnviado());
    }

    @Test
    void entregarDescuentaElStock() {
        pedidoDeMiBotica(reserva(EstadoReserva.LISTA, dentroDeUnDia()));
        when(productos.descontarStock(PRODUCTO, 2)).thenReturn(1);
        guardaLoQueRecibe();

        PedidoResponse resp = service.cambiarEstado(DUENO, ID, "ENTREGADA");

        assertEquals(EstadoReserva.ENTREGADA, resp.estado());
        verify(productos).descontarStock(PRODUCTO, 2);
    }

    @Test
    void entregarSinStockDa409YElPedidoSigueEnLista() {
        pedidoDeMiBotica(reserva(EstadoReserva.LISTA, dentroDeUnDia()));
        when(productos.descontarStock(PRODUCTO, 2)).thenReturn(0);
        when(productos.findById(PRODUCTO)).thenReturn(Optional.of(producto(1, true)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.cambiarEstado(DUENO, ID, "ENTREGADA"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Stock insuficiente: quedan 1 unidades", ex.getMessage());
        verify(reservas, never()).save(any(Reserva.class));
    }

    @Test
    void noSePuedeSaltarDePendienteAEntregada() {
        pedidoDeMiBotica(reserva(EstadoReserva.PENDIENTE, dentroDeUnDia()));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.cambiarEstado(DUENO, ID, "ENTREGADA"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("No se puede pasar de PENDIENTE a ENTREGADA", ex.getMessage());
    }

    @Test
    void unPedidoVencidoNoCambiaDeEstado() {
        Instant ayer = Instant.now().minus(1, ChronoUnit.DAYS);
        pedidoDeMiBotica(reserva(EstadoReserva.PENDIENTE, ayer));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.cambiarEstado(DUENO, ID, "CONFIRMADA"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("La reserva ya venció", ex.getMessage());
    }

    // ---------- Rechazo (H80) ----------

    @Test
    void rechazoConMotivoCortoDa400SinTocarLaBase() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.rechazar(DUENO, ID, "corto"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(boticas, reservas);
    }

    @Test
    void noSePuedeRechazarUnPedidoLista() {
        pedidoDeMiBotica(reserva(EstadoReserva.LISTA, dentroDeUnDia()));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.rechazar(DUENO, ID, "No nos queda stock de esa presentación"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("No se puede rechazar un pedido LISTA", ex.getMessage());
    }

    @Test
    void rechazarUnPedidoPendienteGuardaElMotivoSinEspacios() {
        pedidoDeMiBotica(reserva(EstadoReserva.PENDIENTE, dentroDeUnDia()));
        guardaLoQueRecibe();

        PedidoResponse resp = service.rechazar(DUENO, ID,
                "   No nos queda stock de esa presentación   ");

        assertEquals(EstadoReserva.RECHAZADA, resp.estado());
        assertEquals("No nos queda stock de esa presentación", resp.motivoRechazo());
    }

    // ---------- Detalle y datos del comprador (H78, regla de API.md) ----------

    @Test
    void pedidoDeOtraBoticaOInexistenteDa404() {
        when(boticas.findByUsuarioId(DUENO)).thenReturn(Optional.of(botica()));
        when(reservas.pedidoDeBotica(ID, BOTICA)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.detalle(DUENO, ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("El pedido no existe", ex.getMessage());
    }

    @Test
    void elCompradorSeMuestraComoNombrePlusInicialDelApellido() {
        when(boticas.findByUsuarioId(DUENO)).thenReturn(Optional.of(botica()));
        when(reservas.pedidoDeBotica(ID, BOTICA))
                .thenReturn(Optional.of(reserva(EstadoReserva.PENDIENTE, dentroDeUnDia())));
        when(productos.findAllById(any())).thenReturn(List.of(producto(10, true)));

        Usuario comprador = new Usuario();
        comprador.setId(COMPRADOR);
        comprador.setNombre("Carlos Ramírez");
        when(usuarios.findAllById(any())).thenReturn(List.of(comprador));

        PedidoResponse resp = service.detalle(DUENO, ID);

        assertEquals("Carlos R.", resp.compradorNombre());
        assertEquals(0, new BigDecimal("4.40").compareTo(resp.total()));
        assertEquals(10, resp.stockActual());
    }

    // ---------- H72: condición de venta ----------

    @Test
    void elPedidoTraeLaCondicionActualDelMedicamentoEnlazado() {
        when(boticas.findByUsuarioId(DUENO)).thenReturn(Optional.of(botica()));
        when(reservas.pedidosDeBotica(eq(BOTICA), any(Pageable.class)))
                .thenReturn(List.of(reserva(EstadoReserva.CONFIRMADA, dentroDeUnDia())));
        Producto p = producto(10, true);
        p.setMedicamentoId(40L);
        when(productos.findAllById(any())).thenReturn(List.of(p));
        when(condiciones.deMedicamentos(List.of(40L))).thenReturn(Map.of(40L, "CON_RECETA"));

        List<PedidoResponse> lista = service.listar(DUENO, null);

        assertEquals("CON_RECETA", lista.get(0).condicionVenta());
    }

    @Test
    void elPedidoDeUnProductoSinEnlazarTraeCondicionNula() {
        when(boticas.findByUsuarioId(DUENO)).thenReturn(Optional.of(botica()));
        when(reservas.pedidoDeBotica(ID, BOTICA))
                .thenReturn(Optional.of(reserva(EstadoReserva.CONFIRMADA, dentroDeUnDia())));
        when(productos.findAllById(any())).thenReturn(List.of(producto(10, true)));

        assertNull(service.detalle(DUENO, ID).condicionVenta());
    }
}
