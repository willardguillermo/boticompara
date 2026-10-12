package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    BoticaService boticas;

    @Mock
    ProductoRepository productos;

    @InjectMocks
    CatalogoService service;

    private void conBotica() {
        Botica b = new Botica();
        b.setId(1L);
        when(boticas.obtenerMia(10L)).thenReturn(b);
    }

    private Producto producto(Long id, String nombre, int stock, int stockMinimo) {
        Producto p = new Producto();
        p.setId(id);
        p.setBoticaId(1L);
        p.setNombreComercial(nombre);
        p.setPrincipioActivo("Principio");
        p.setPresentacion("Tableta");
        p.setPrecio(new BigDecimal("1.00"));
        p.setStock(stock);
        p.setStockMinimo(stockMinimo);
        return p;
    }

    private List<Long> ids(List<ProductoResponse> lista) {
        return lista.stream().map(ProductoResponse::id).toList();
    }

    @Test
    void sinFiltrosDevuelveTodoElCatalogoConSuStockMinimo() {
        conBotica();
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(1L))
                .thenReturn(List.of(producto(1L, "A", 3, 5), producto(2L, "B", 50, 8)));

        List<ProductoResponse> r = service.listar(10L, null, false);

        assertEquals(List.of(1L, 2L), ids(r));
        assertEquals(5, r.get(0).stockMinimo());
        assertEquals(8, r.get(1).stockMinimo());
    }

    @Test
    void stockBajoDevuelveSoloLosQueEstanEnOBajoElMinimo() {
        conBotica();
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(1L)).thenReturn(List.of(
                producto(1L, "A", 3, 5),   // bajo el mínimo
                producto(2L, "B", 5, 5),   // justo en el mínimo
                producto(3L, "C", 6, 5),   // por encima
                producto(4L, "D", 0, 0))); // sin stock y mínimo 0

        List<ProductoResponse> r = service.listar(10L, "  ", true);

        assertEquals(List.of(1L, 2L, 4L), ids(r));
    }

    @Test
    void stockBajoSeCombinaConLaBusqueda() {
        conBotica();
        when(productos.buscarEnBotica(1L, "%para%")).thenReturn(List.of(
                producto(1L, "Paracetamol", 2, 5),
                producto(2L, "Panadol", 40, 5)));

        List<ProductoResponse> r = service.listar(10L, "  PARA ", true);

        assertEquals(List.of(1L), ids(r));
    }
}