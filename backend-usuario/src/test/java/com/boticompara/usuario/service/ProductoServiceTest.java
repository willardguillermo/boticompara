package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ProductoRequest;
import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    BoticaRepository boticas;

    @Mock
    ProductoRepository productos;

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
        return new ProductoRequest("Panadol", "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal("8.50"), 40, stockMinimo);
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
}