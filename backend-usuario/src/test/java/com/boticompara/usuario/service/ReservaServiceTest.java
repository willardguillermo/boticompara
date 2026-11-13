package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ReservaResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Reserva;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final long USUARIO = 10L;
    private static final long PRODUCTO = 1L;
    private static final long BOTICA = 2L;
    private static final String CODIGO_VALIDO = "BC-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}";

    @Mock ProductoRepository productos;
    @Mock BoticaRepository boticas;
    @Mock ReservaRepository reservas;
    @Mock BusquedaService busqueda;

    ReservaService service;

    @BeforeEach
    void preparar() {
        service = new ReservaService(productos, boticas, reservas, busqueda);
    }

    private Producto producto(int stock) {
        Producto p = new Producto();
        p.setId(PRODUCTO);
        p.setBoticaId(BOTICA);
        p.setNombreComercial("Paracetamol Genérico");
        p.setPresentacion("Tableta 500 mg x 10");
        p.setPrecio(new BigDecimal("2.20"));
        p.setStock(stock);
        p.setActivo(true);
        return p;
    }

    private Botica botica() {
        Botica b = new Botica();
        b.setId(BOTICA);
        b.setNombreComercial("FarmaVida");
        b.setDireccion("Jr. Trujillo 210");
        b.setDistrito("Lurigancho-Chosica");
        b.setTelefono("014567890");
        return b;
    }

    /** Lo que se necesita para que una reserva llegue a guardarse. */
    private void stubCreacionExitosa(Producto p) {
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(p));
        when(boticas.findById(BOTICA)).thenReturn(Optional.of(botica()));
        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cantidadFueraDeRangoNoLlegaABuscarNada() {
        assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 0));
        assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 11));
        assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, null));

        verifyNoInteractions(busqueda, productos, reservas);
    }

    @Test
    void productoNoVisibleEs404ConElMensajeDelContrato() {
        when(busqueda.detalle(PRODUCTO)).thenThrow(ApiException.notFound("Producto no encontrado"));

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("El producto no está disponible", ex.getMessage());
        verifyNoInteractions(reservas);
    }

    @Test
    void yaTieneUnaReservaActivaDelMismoProductoEs409() {
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(5)));
        when(reservas.existsByUsuarioIdAndProductoIdAndEstadoIn(USUARIO, PRODUCTO, EstadoReserva.ACTIVAS))
                .thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Ya tienes una reserva activa de este producto", ex.getMessage());
        verify(reservas, times(0)).save(any(Reserva.class));
    }

    @Test
    void cantidadMayorQueLaDisponibleEs409() {
        // stock 5 y 3 apartadas por otros compradores: quedan 2
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(5)));
        when(reservas.unidadesApartadas(any(), any())).thenReturn(3L);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 3));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Solo hay 2 unidades disponibles de este producto", ex.getMessage());
    }

    @Test
    void sinUnidadesDisponiblesEs409() {
        // stock 5 y 5 apartadas: disponible 0
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(5)));
        when(reservas.unidadesApartadas(any(), any())).thenReturn(5L);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Todas las unidades de este producto ya están reservadas", ex.getMessage());
    }

    @Test
    void reservaNuevaQuedaPendienteConElPrecioDelProductoYVenceEn24Horas() {
        stubCreacionExitosa(producto(10));

        ReservaResponse r = service.crear(USUARIO, PRODUCTO, 2);

        ArgumentCaptor<Reserva> captura = ArgumentCaptor.forClass(Reserva.class);
        verify(reservas).save(captura.capture());
        Reserva guardada = captura.getValue();

        assertEquals(EstadoReserva.PENDIENTE, guardada.getEstado());
        assertEquals(0, new BigDecimal("2.20").compareTo(guardada.getPrecioUnitario()));
        assertEquals(USUARIO, guardada.getUsuarioId());
        assertEquals(PRODUCTO, guardada.getProductoId());
        assertEquals(2, guardada.getCantidad());
        assertTrue(guardada.getVenceEn().isAfter(Instant.now().plus(23, ChronoUnit.HOURS)));
        assertTrue(guardada.getVenceEn().isBefore(Instant.now().plus(25, ChronoUnit.HOURS)));

        assertEquals(0, new BigDecimal("4.40").compareTo(r.total()));
        assertEquals("FarmaVida", r.boticaNombre());
    }

    @Test
    void codigoRepetidoSeVuelveAGenerar() {
        stubCreacionExitosa(producto(10));
        when(reservas.existsByCodigo(anyString())).thenReturn(true, false);

        ReservaResponse r = service.crear(USUARIO, PRODUCTO, 1);

        verify(reservas, times(2)).existsByCodigo(anyString());
        assertTrue(r.codigo().matches(CODIGO_VALIDO), "código: " + r.codigo());
    }

    @Test
    void historialTraeDatosDelProductoAunqueYaNoSeVea() {
        Reserva r = new Reserva();
        r.setId(15L);
        r.setCodigo("BC-7K2QXM");
        r.setUsuarioId(USUARIO);
        r.setProductoId(PRODUCTO);
        r.setCantidad(2);
        r.setPrecioUnitario(new BigDecimal("2.20"));
        r.setEstado(EstadoReserva.PENDIENTE);
        r.setVenceEn(Instant.now());

        // El producto ya no tiene stock: no aparece en la búsqueda, pero la reserva sigue visible
        when(reservas.findTop100ByUsuarioIdOrderByCreadoEnDesc(USUARIO)).thenReturn(List.of(r));
        when(productos.findAllById(any())).thenReturn(List.of(producto(0)));
        when(boticas.findAllById(any())).thenReturn(List.of(botica()));

        List<ReservaResponse> lista = service.misReservas(USUARIO, null);

        assertEquals(1, lista.size());
        assertEquals("Paracetamol Genérico", lista.get(0).nombreComercial());
        assertEquals("FarmaVida", lista.get(0).boticaNombre());
        assertEquals(0, new BigDecimal("4.40").compareTo(lista.get(0).total()));
    }

    @Test
    void historialFiltraPorEstadoCuandoSeIndica() {
        when(reservas.findTop100ByUsuarioIdAndEstadoOrderByCreadoEnDesc(USUARIO, EstadoReserva.LISTA))
                .thenReturn(List.of());

        List<ReservaResponse> lista = service.misReservas(USUARIO, EstadoReserva.LISTA);

        assertTrue(lista.isEmpty());
        verify(reservas).findTop100ByUsuarioIdAndEstadoOrderByCreadoEnDesc(USUARIO, EstadoReserva.LISTA);
        verify(reservas, times(0)).findTop100ByUsuarioIdOrderByCreadoEnDesc(USUARIO);
    }
}