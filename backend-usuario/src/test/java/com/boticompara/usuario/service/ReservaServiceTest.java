package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ReservaResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
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

    @Mock
    ApplicationEventPublisher eventos;

    @Mock CondicionVentaService condiciones;
    @Mock UsuarioRepository usuarios;


    ReservaService service;

    @BeforeEach
    void preparar() {

        service = new ReservaService(productos, boticas, reservas, busqueda, eventos, condiciones, usuarios);
        // Por defecto el comprador ya verificó su correo
        lenient().when(usuarios.bloquearPorId(USUARIO)).thenReturn(Optional.of(comprador(true)));
    }

    private static Usuario comprador(boolean verificado) {
        Usuario u = new Usuario();
        u.setId(USUARIO);
        u.setCorreoVerificado(verificado);
        return u;
    }

    @Test
    void crear_sinCorreoVerificado_403SinTocarNada() {
        when(usuarios.bloquearPorId(USUARIO)).thenReturn(Optional.of(comprador(false)));

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Verifica tu correo para poder reservar", ex.getMessage());
        verifyNoInteractions(busqueda, productos, reservas, eventos);
    }

    @Test
    void crear_usuarioQueNoExiste_403() {
        when(usuarios.bloquearPorId(USUARIO)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void crear_cantidadInvalida_el400VaAntesQueEl403() {
        lenient().when(usuarios.bloquearPorId(USUARIO)).thenReturn(Optional.of(comprador(false)));

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 0));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
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
    void productoDesactivadoMientrasSeEsperabaElBloqueoEs404() {
        // H19: la búsqueda todavía lo veía, pero el dueño eliminó su cuenta antes de que se obtuviera el bloqueo
        Producto desactivado = producto(5);
        desactivado.setActivo(false);
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(desactivado));

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("El producto no está disponible", ex.getMessage());
        verifyNoInteractions(reservas, condiciones, eventos);
    }

    @Test
    void yaTieneUnaReservaActivaDelMismoProductoEs409() {
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(5)));
        when(reservas.existeActivaDelProducto(eq(USUARIO), eq(PRODUCTO), eq(EstadoReserva.ACTIVAS), any()))
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
        when(reservas.unidadesApartadas(any(), any(), any())).thenReturn(3L);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 3));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Solo hay 2 unidades disponibles de este producto", ex.getMessage());
    }

    @Test
    void sinUnidadesDisponiblesEs409() {
        // stock 5 y 5 apartadas: disponible 0
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(5)));
        when(reservas.unidadesApartadas(any(), any(), any())).thenReturn(5L);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Todas las unidades de este producto ya están reservadas", ex.getMessage());
    }

    // ---------- H72: condición de venta ----------

    private static final long MEDICAMENTO = 40L;

    private Producto enlazado(int stock) {
        Producto p = producto(stock);
        p.setMedicamentoId(MEDICAMENTO);
        return p;
    }

    @Test
    void recetaRetenidaEs409ConElMensajeDelContratoYNoCreaNada() {
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(enlazado(10)));
        when(condiciones.deMedicamento(MEDICAMENTO)).thenReturn("RECETA_RETENIDA");

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Este medicamento se vende con receta retenida: acércate a la botica con tu receta médica",
                ex.getMessage());
        // Antes de calcular lo disponible y sin guardar nada
        verifyNoInteractions(reservas);
    }

    @Test
    void productoNoVisibleEs404AunqueSeaRecetaRetenida() {
        when(busqueda.detalle(PRODUCTO)).thenThrow(ApiException.notFound("Producto no encontrado"));

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(condiciones);
    }

    @Test
    void conRecetaSiSeReservaYLaRespuestaTraeLaCondicion() {
        stubCreacionExitosa(enlazado(10));
        when(condiciones.deMedicamento(MEDICAMENTO)).thenReturn("CON_RECETA");

        ReservaResponse r = service.crear(USUARIO, PRODUCTO, 2);

        verify(reservas).save(any(Reserva.class));
        assertEquals(EstadoReserva.PENDIENTE, r.estado());
        assertEquals("CON_RECETA", r.condicionVenta());
    }

    @Test
    void productoSinEnlazarSeReservaConCondicionNula() {
        stubCreacionExitosa(producto(10));

        ReservaResponse r = service.crear(USUARIO, PRODUCTO, 1);

        verify(reservas).save(any(Reserva.class));
        assertNull(r.condicionVenta());
    }

    @Test
    void historialYDetalleTraenLaCondicionActualDelMedicamento() {
        Reserva r = reservaDe(EstadoReserva.PENDIENTE, Instant.now().plus(1, ChronoUnit.DAYS));
        when(reservas.findTop100ByUsuarioIdOrderByCreadoEnDesc(USUARIO)).thenReturn(List.of(r));
        when(productos.findAllById(any())).thenReturn(List.of(enlazado(3)));
        when(boticas.findAllById(any())).thenReturn(List.of(botica()));
        when(condiciones.deMedicamentos(List.of(MEDICAMENTO))).thenReturn(Map.of(MEDICAMENTO, "CON_RECETA"));
        when(reservas.findByIdAndUsuarioId(15L, USUARIO)).thenReturn(Optional.of(r));
        when(productos.findById(PRODUCTO)).thenReturn(Optional.of(enlazado(3)));
        when(boticas.findById(BOTICA)).thenReturn(Optional.of(botica()));
        when(condiciones.deMedicamento(MEDICAMENTO)).thenReturn("RECETA_RETENIDA");

        assertEquals("CON_RECETA", service.misReservas(USUARIO, null).get(0).condicionVenta());
        assertEquals("RECETA_RETENIDA", service.detalle(USUARIO, 15L).condicionVenta());
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

    private Reserva reservaDe(EstadoReserva estado, Instant venceEn) {
        Reserva r = new Reserva();
        r.setId(15L);
        r.setCodigo("BC-7K2QXM");
        r.setUsuarioId(USUARIO);
        r.setProductoId(PRODUCTO);
        r.setCantidad(2);
        r.setPrecioUnitario(new BigDecimal("2.20"));
        r.setEstado(estado);
        r.setVenceEn(venceEn);
        return r;
    }

    @Test
    void cancelarUnaReservaPendienteLaPasaACancelada() {
        when(reservas.bloquearPropia(15L, USUARIO))
                .thenReturn(Optional.of(reservaDe(EstadoReserva.PENDIENTE, Instant.now().plus(1, ChronoUnit.DAYS))));
        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productos.findById(PRODUCTO)).thenReturn(Optional.of(producto(3)));
        when(boticas.findById(BOTICA)).thenReturn(Optional.of(botica()));

        ReservaResponse resp = service.cancelar(USUARIO, 15L);

        assertEquals(EstadoReserva.CANCELADA, resp.estado());
        verify(eventos).publishEvent(new ReservaCancelada(15L));
    }

    @Test
    void cancelarUnaReservaEntregadaDa409() {
        when(reservas.bloquearPropia(15L, USUARIO))
                .thenReturn(Optional.of(reservaDe(EstadoReserva.ENTREGADA, Instant.now().plus(1, ChronoUnit.DAYS))));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancelar(USUARIO, 15L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("No se puede cancelar una reserva ENTREGADA", ex.getMessage());
        verify(reservas, times(0)).save(any(Reserva.class));
        verifyNoInteractions(eventos);
    }

    @Test
    void cancelarUnaReservaYaVencidaDa409() {
        when(reservas.bloquearPropia(15L, USUARIO))
                .thenReturn(Optional.of(reservaDe(EstadoReserva.PENDIENTE, Instant.now().minus(1, ChronoUnit.MINUTES))));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancelar(USUARIO, 15L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("La reserva ya venció", ex.getMessage());
    }

    @Test
    void cancelarUnaReservaAjenaEs404() {
        when(reservas.bloquearPropia(15L, USUARIO)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.cancelar(USUARIO, 15L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void detalleDeUnaReservaPropiaLaDevuelve() {
        when(reservas.findByIdAndUsuarioId(15L, USUARIO))
                .thenReturn(Optional.of(reservaDe(EstadoReserva.PENDIENTE, Instant.now().plus(1, ChronoUnit.DAYS))));
        when(productos.findById(PRODUCTO)).thenReturn(Optional.of(producto(3)));
        when(boticas.findById(BOTICA)).thenReturn(Optional.of(botica()));

        ReservaResponse resp = service.detalle(USUARIO, 15L);

        assertEquals("BC-7K2QXM", resp.codigo());
        assertEquals("FarmaVida", resp.boticaNombre());
    }

    // ---------- H19: el comprador elimina su cuenta ----------

    @Test
    void cancelarActivasDeLasCancelaYAvisaACadaDueno() {
        Reserva pendiente = reservaDe(EstadoReserva.PENDIENTE, Instant.now().plus(1, ChronoUnit.DAYS));
        // Aunque ya haya pasado su venceEn: nadie la va a recoger
        Reserva lista = reservaDe(EstadoReserva.LISTA, Instant.now().minus(1, ChronoUnit.HOURS));
        lista.setId(16L);
        when(reservas.bloquearPropiasConEstados(USUARIO, EstadoReserva.ACTIVAS)).thenReturn(List.of(pendiente, lista));
        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(2, service.cancelarActivasDe(USUARIO));

        assertEquals(EstadoReserva.CANCELADA, pendiente.getEstado());
        assertEquals(EstadoReserva.CANCELADA, lista.getEstado());
        assertNull(pendiente.getMotivoRechazo());
        verify(eventos).publishEvent(new ReservaCancelada(15L));
        verify(eventos).publishEvent(new ReservaCancelada(16L));
    }

    @Test
    void cancelarActivasDeSinReservasActivasNoAvisaANadie() {
        when(reservas.bloquearPropiasConEstados(USUARIO, java.util.EnumSet.of(EstadoReserva.PENDIENTE,
                EstadoReserva.CONFIRMADA, EstadoReserva.LISTA))).thenReturn(List.of());

        assertEquals(0, service.cancelarActivasDe(USUARIO));

        verify(reservas, times(0)).save(any(Reserva.class));
        verifyNoInteractions(eventos);
    }

    // ---------- S4-01: las reservas con venceEn pasado no cuentan ----------

    @Test
    void lasReglasDeReservasActivasUsanLaHoraActual() {
        Producto p = producto(5);
        stubCreacionExitosa(p);
        Instant antes = Instant.now();

        service.crear(USUARIO, PRODUCTO, 1);

        ArgumentCaptor<Instant> enExiste = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> enContar = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> enApartadas = ArgumentCaptor.forClass(Instant.class);
        verify(reservas).existeActivaDelProducto(eq(USUARIO), eq(PRODUCTO), eq(EstadoReserva.ACTIVAS), enExiste.capture());
        verify(reservas).contarActivasDe(eq(USUARIO), eq(EstadoReserva.ACTIVAS), enContar.capture());
        verify(reservas).unidadesApartadas(eq(PRODUCTO), eq(EstadoReserva.ACTIVAS), enApartadas.capture());
        // La misma hora en las tres, y es la de ahora: así no cuentan las que ya pasaron venceEn
        assertEquals(enExiste.getValue(), enContar.getValue());
        assertEquals(enExiste.getValue(), enApartadas.getValue());
        assertTrue(!enExiste.getValue().isBefore(antes) && !enExiste.getValue().isAfter(Instant.now()));
    }

    @Test
    void crearBloqueaLaFilaDelComprador() {
        stubCreacionExitosa(producto(5));

        service.crear(USUARIO, PRODUCTO, 1);

        verify(usuarios).bloquearPorId(USUARIO);
        verify(usuarios, never()).findById(any());
    }

    @Test
    void cancelarUnaReservaVencidaDa409ConElMensajeDeVencida() {
        when(reservas.bloquearPropia(15L, USUARIO))
                .thenReturn(Optional.of(reservaDe(EstadoReserva.VENCIDA, Instant.now().minus(2, ChronoUnit.HOURS))));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancelar(USUARIO, 15L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("La reserva ya venció", ex.getMessage());
        verify(reservas, never()).save(any(Reserva.class));
        verifyNoInteractions(eventos);
    }

    // ---------- S4-03: máximo de reservas activas por comprador ----------

    @Test
    void conCincoReservasActivasEs409YNoCalculaElStock() {
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(5)));
        when(reservas.contarActivasDe(eq(USUARIO), eq(EstadoReserva.ACTIVAS), any())).thenReturn(5L);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Tienes demasiadas reservas activas: el máximo es 5. Recoge o cancela alguna antes de hacer otra",
                ex.getMessage());
        verify(reservas, never()).unidadesApartadas(any(), any(), any());
        verify(reservas, never()).save(any(Reserva.class));
        verifyNoInteractions(eventos);
    }

    @Test
    void conCuatroReservasActivasTodaviaPuedeReservar() {
        stubCreacionExitosa(producto(5));
        when(reservas.contarActivasDe(eq(USUARIO), eq(EstadoReserva.ACTIVAS), any())).thenReturn(4L);

        ReservaResponse resp = service.crear(USUARIO, PRODUCTO, 1);

        assertEquals(EstadoReserva.PENDIENTE, resp.estado());
    }

    @Test
    void elMaximoSeConfigura() {
        service.setMaximoActivas(2);
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(5)));
        when(reservas.contarActivasDe(eq(USUARIO), eq(EstadoReserva.ACTIVAS), any())).thenReturn(2L);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertTrue(ex.getMessage().startsWith("Tienes demasiadas reservas activas: el máximo es 2."));
    }

    @Test
    void laReservaActivaDelMismoProductoSeRevisaAntesQueElMaximo() {
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(producto(5)));
        when(reservas.existeActivaDelProducto(eq(USUARIO), eq(PRODUCTO), eq(EstadoReserva.ACTIVAS), any()))
                .thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(USUARIO, PRODUCTO, 1));

        assertEquals("Ya tienes una reserva activa de este producto", ex.getMessage());
        verify(reservas, never()).contarActivasDe(any(), any(), any());
    }
}
