package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ProductoRequest;
import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.error.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    BoticaRepository boticas;

    @Mock
    ProductoRepository productos;

    @Mock
    MedicamentoService medicamentos;

    @Mock
    AlertaService alertas;

    @Mock
    ApplicationEventPublisher eventos;

    @InjectMocks
    ProductoService service;

    private void conBoticaAprobada() {
        Botica b = new Botica();
        b.setId(1L);
        b.setUsuarioId(10L);
        b.setEstado("APROBADO");
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
    }

    private ProductoRequest request(Integer stockMinimo) {
        return request(stockMinimo, null);
    }

    private ProductoRequest request(Integer stockMinimo, Long medicamentoId) {
        return new ProductoRequest("Panadol", "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal("8.50"), 40, stockMinimo, medicamentoId);
    }

    private Producto productoExistente(int stockMinimo) {
        Producto p = new Producto();
        p.setId(7L);
        p.setBoticaId(1L);
        p.setNombreComercial("Panadol");
        p.setPrincipioActivo("Paracetamol");
        p.setPresentacion("Tableta 500 mg x 10");
        p.setPrecio(new BigDecimal("8.50"));
        p.setStock(10);
        p.setStockMinimo(stockMinimo);
        return p;
    }

    @Test
    void crearSinStockMinimoUsaElValorPorDefecto() {
        conBoticaAprobada();
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.crear(10L, request(null));

        assertEquals(5, r.stockMinimo());
        assertEquals(40, r.stock());
    }

    @Test
    void crearConStockMinimoLoGuarda() {
        conBoticaAprobada();
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.crear(10L, request(12));

        assertEquals(12, r.stockMinimo());
    }

    @Test
    void actualizarSinStockMinimoConservaElActual() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(productoExistente(9)));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.actualizar(10L, 7L, request(null));

        assertEquals(9, r.stockMinimo());
        assertEquals(40, r.stock());
    }

    @Test
    void actualizarConStockMinimoLoCambia() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(productoExistente(9)));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.actualizar(10L, 7L, request(20));

        assertEquals(20, r.stockMinimo());
    }

    // ---------- H31: enlace con el catálogo maestro ----------

    @Test
    void crearSinMedicamento_noConsultaElCatalogo() {
        conBoticaAprobada();
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.crear(10L, request(null));

        assertNull(r.medicamentoId());
        verify(medicamentos, never()).principioActivoEnlazable(any());
    }

    @Test
    void crearConMedicamentoValido_loEnlaza() {
        conBoticaAprobada();
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.crear(10L, request(null, 12L));

        assertEquals(12L, r.medicamentoId());
        verify(medicamentos).principioActivoEnlazable(12L);
    }

    @Test
    void crearConMedicamentoInvalido_noGuarda() {
        conBoticaAprobada();
        doThrow(ApiException.badRequest("El medicamento no existe en el catálogo"))
                .when(medicamentos).principioActivoEnlazable(99L);

        ApiException ex = assertThrows(ApiException.class, () -> service.crear(10L, request(null, 99L)));

        assertEquals("El medicamento no existe en el catálogo", ex.getMessage());
        verify(productos, never()).save(any(Producto.class));
    }

    @Test
    void actualizarSinMedicamento_conservaElEnlaceActual() {
        conBoticaAprobada();
        Producto existente = productoExistente(9);
        existente.setMedicamentoId(12L);
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.actualizar(10L, 7L, request(null));

        assertEquals(12L, r.medicamentoId());
    }

    @Test
    void actualizarConOtroMedicamento_loCambia() {
        conBoticaAprobada();
        Producto existente = productoExistente(9);
        existente.setMedicamentoId(12L);
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.actualizar(10L, 7L, request(null, 15L));

        assertEquals(15L, r.medicamentoId());
        verify(medicamentos).principioActivoEnlazable(15L);
    }

    // ---------- D2: al enlazar se copia el principio activo del medicamento ----------

    private ProductoRequest requestConPrincipio(String principioActivo, Long medicamentoId) {
        return new ProductoRequest("Panadol", principioActivo, "Tableta 500 mg x 10",
                new BigDecimal("8.50"), 40, null, medicamentoId);
    }

    @Test
    void crearConMedicamento_copiaElPrincipioActivoDelMedicamento() {
        conBoticaAprobada();
        when(medicamentos.principioActivoEnlazable(12L)).thenReturn("Paracetamol");
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.crear(10L, requestConPrincipio("paracetamol 500", 12L));

        assertEquals("Paracetamol", r.principioActivo());
    }

    @Test
    void actualizarConMedicamento_copiaElPrincipioActivoDelMedicamento() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(productoExistente(9)));
        when(medicamentos.principioActivoEnlazable(15L)).thenReturn("Ibuprofeno");
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.actualizar(10L, 7L, requestConPrincipio("Paracetamol", 15L));

        assertEquals("Ibuprofeno", r.principioActivo());
    }

    @Test
    void crearSinMedicamento_usaElPrincipioActivoEnviado() {
        conBoticaAprobada();
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.crear(10L, requestConPrincipio("  Amoxicilina ", null));

        assertEquals("Amoxicilina", r.principioActivo());
    }

    @Test
    void actualizarSinMedicamento_yaEnlazado_mantieneElPrincipioActivoDelMedicamento() {
        conBoticaAprobada();
        Producto existente = productoExistente(9);
        existente.setMedicamentoId(12L);
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));
        when(medicamentos.principioActivoDe(12L)).thenReturn("Paracetamol");
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.actualizar(10L, 7L, requestConPrincipio("Otro texto", null));

        assertEquals("Paracetamol", r.principioActivo());
        assertEquals(12L, r.medicamentoId());
        verify(medicamentos, never()).principioActivoEnlazable(any());
    }

    @Test
    void actualizarSinMedicamento_sinEnlace_usaElPrincipioActivoEnviado() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(productoExistente(9)));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponse r = service.actualizar(10L, 7L, requestConPrincipio("Amoxicilina", null));

        assertEquals("Amoxicilina", r.principioActivo());
        verify(medicamentos, never()).principioActivoDe(any());
    }

    @Test
    void desenlazarMedicamento_conservaElPrincipioActivoCopiado() {
        conBoticaAprobada();
        Producto existente = productoExistente(9);
        existente.setMedicamentoId(15L);
        existente.setPrincipioActivo("Ibuprofeno");
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));

        service.desenlazarMedicamento(10L, 7L);

        assertNull(existente.getMedicamentoId());
        assertEquals("Ibuprofeno", existente.getPrincipioActivo());
    }


    // ---------- H31: quitar el enlace (DELETE /productos/{id}/medicamento) ----------

    @Test
    void desenlazarMedicamento_quitaElEnlaceYConservaElProducto() {
        conBoticaAprobada();
        Producto existente = productoExistente(9);
        existente.setMedicamentoId(12L);
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));

        service.desenlazarMedicamento(10L, 7L);

        assertNull(existente.getMedicamentoId());
        assertTrue(existente.isActivo());
        assertEquals("Panadol", existente.getNombreComercial());
        assertEquals(new BigDecimal("8.50"), existente.getPrecio());
        verify(productos).save(existente);
    }

    @Test
    void desenlazarMedicamento_sinEnlace_noHaceNadaYNoFalla() {
        conBoticaAprobada();
        Producto existente = productoExistente(9);
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));

        service.desenlazarMedicamento(10L, 7L);

        assertNull(existente.getMedicamentoId());
        verify(productos, never()).save(any(Producto.class));
    }

    @Test
    void desenlazarMedicamento_productoAjenoOInexistente_responde404() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.desenlazarMedicamento(10L, 99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Producto no encontrado", ex.getMessage());
        verify(productos, never()).save(any(Producto.class));
    }

    @Test
    void desenlazarMedicamento_sinBoticaAprobada_responde403() {
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.desenlazarMedicamento(10L, 7L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(productos, never()).findByIdAndBoticaIdAndActivoTrue(any(), any());
        verify(productos, never()).save(any(Producto.class));
    }


    // ---------- H59: alertas cuando baja el precio ----------

    private ProductoRequest requestConPrecio(String precio) {
        return new ProductoRequest("Panadol", "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal(precio), 40, null, null);
    }

    @Test
    void actualizarBajandoElPrecio_registraLaBajaDespuesDeGuardar() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(productoExistente(9)));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        service.actualizar(10L, 7L, requestConPrecio("7.50"));

        InOrder orden = inOrder(productos, alertas);
        orden.verify(productos).save(any(Producto.class));
        orden.verify(productos).flush();
        orden.verify(alertas).registrarBaja(7L, new BigDecimal("8.50"), new BigDecimal("7.50"));
    }

    @Test
    void actualizarSinBajarElPrecio_noRegistraAlertas() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(productoExistente(9)));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        service.actualizar(10L, 7L, requestConPrecio("8.50"));
        service.actualizar(10L, 7L, requestConPrecio("9.90"));

        verifyNoInteractions(alertas);
        verify(productos, never()).flush();
    }
    @Test
    void actualizarBajandoElPrecio_publicaElEventoConLasAlertasCreadas() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(productoExistente(9)));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        AlertaService.AlertaCreada creada = new AlertaService.AlertaCreada(1L, 42L, 7L,
                new BigDecimal("8.50"), new BigDecimal("7.50"));
        when(alertas.registrarBaja(7L, new BigDecimal("8.50"), new BigDecimal("7.50"))).thenReturn(List.of(creada));

        service.actualizar(10L, 7L, requestConPrecio("7.50"));

        verify(eventos).publishEvent(new AlertasCreadas(List.of(creada)));
    }

    @Test
    void actualizarBajandoElPrecio_sinAlertasCreadas_noPublicaEvento() {
        conBoticaAprobada();
        when(productos.findByIdAndBoticaIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(productoExistente(9)));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        service.actualizar(10L, 7L, requestConPrecio("7.50"));

        verifyNoInteractions(eventos);
    }
}