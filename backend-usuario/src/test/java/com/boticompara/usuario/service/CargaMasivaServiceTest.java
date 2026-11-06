package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ErrorFila;
import com.boticompara.usuario.dto.ResultadoCarga;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import org.mockito.InOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CargaMasivaServiceTest {

    private static final String CAB = "nombreComercial,principioActivo,presentacion,precio,stock,stockMinimo";

    @Mock
    BoticaRepository boticas;

    @Mock
    ProductoRepository productos;

    @Mock
    AlertaService alertas;

    @Mock
    ApplicationEventPublisher eventos;

    @Captor
    ArgumentCaptor<List<Producto>> guardados;

    @InjectMocks
    CargaMasivaService service;

    private static byte[] csv(String... lineas) {
        return String.join("\n", lineas).getBytes(StandardCharsets.UTF_8);
    }

    private void conBotica(String estado) {
        Botica b = new Botica();
        b.setId(1L);
        b.setUsuarioId(10L);
        b.setEstado(estado);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
    }

    private void conCatalogo(Producto... existentes) {
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(1L)).thenReturn(List.of(existentes));
    }

    private Producto producto(Long id, String nombre, String presentacion, String precio, int stock, int stockMinimo) {
        Producto p = new Producto();
        p.setId(id);
        p.setBoticaId(1L);
        p.setNombreComercial(nombre);
        p.setPrincipioActivo("Principio");
        p.setPresentacion(presentacion);
        p.setPrecio(new BigDecimal(precio));
        p.setStock(stock);
        p.setStockMinimo(stockMinimo);
        return p;
    }

    @Test
    void creaLosNuevosYActualizaLosExistentes() {
        conBotica("APROBADO");
        Producto panadol = producto(7L, "Panadol", "Tableta 500 mg x 10", "8.50", 40, 5);
        conCatalogo(panadol);

        ResultadoCarga r = service.cargar(10L, csv(CAB,
                "panadol,Paracetamol,TABLETA 500 mg x 10,9.00,35,",
                "Ibuprofeno Genérico,Ibuprofeno,Tableta 400 mg x 10,3.20,100,10"));

        assertEquals(1, r.creados());
        assertEquals(1, r.actualizados());
        assertTrue(r.errores().isEmpty());
        assertEquals(new BigDecimal("9.00"), panadol.getPrecio());
        assertEquals(35, panadol.getStock());
        assertEquals(5, panadol.getStockMinimo());

        verify(productos).saveAll(guardados.capture());
        assertEquals(2, guardados.getValue().size());
        Producto nuevo = guardados.getValue().stream().filter(p -> p.getId() == null).findFirst().orElseThrow();
        assertEquals(1L, nuevo.getBoticaId());
        assertEquals("Ibuprofeno Genérico", nuevo.getNombreComercial());
        assertEquals("Ibuprofeno", nuevo.getPrincipioActivo());
        assertEquals(new BigDecimal("3.20"), nuevo.getPrecio());
        assertEquals(100, nuevo.getStock());
        assertEquals(10, nuevo.getStockMinimo());
        assertTrue(nuevo.isActivo());
    }

    @Test
    void actualizaElStockMinimoSoloSiLaFilaLoTrae() {
        conBotica("APROBADO");
        Producto a = producto(1L, "A", "Tableta", "1.00", 10, 5);
        Producto b = producto(2L, "B", "Tableta", "1.00", 10, 7);
        conCatalogo(a, b);

        service.cargar(10L, csv(CAB,
                "A,Principio,Tableta,2.00,20,20",
                "B,Principio,Tableta,2.00,20,"));

        assertEquals(20, a.getStockMinimo());
        assertEquals(7, b.getStockMinimo());
    }

    @Test
    void sinStockMinimoUsaElPorDefectoAlCrear() {
        conBotica("APROBADO");
        conCatalogo();

        service.cargar(10L, csv(CAB, "Nuevo,Principio,Tableta,2.00,20,"));

        verify(productos).saveAll(guardados.capture());
        assertEquals(5, guardados.getValue().get(0).getStockMinimo());
    }

    @Test
    void devuelveLosErroresPorFilaYGuardaLasFilasValidas() {
        conBotica("APROBADO");
        conCatalogo();

        ResultadoCarga r = service.cargar(10L, csv(CAB,
                "Panadol,Paracetamol,Tableta,8.50,40,5",
                "Malo,Paracetamol,Tableta,0,40,5"));

        assertEquals(1, r.creados());
        assertEquals(0, r.actualizados());
        assertEquals(List.of(new ErrorFila(3, "El precio debe ser mayor a 0.")), r.errores());
        verify(productos).saveAll(guardados.capture());
        assertEquals(1, guardados.getValue().size());
    }

    @Test
    void noGuardaNadaSiTodasLasFilasSonInvalidas() {
        conBotica("APROBADO");
        conCatalogo();

        ResultadoCarga r = service.cargar(10L, csv(CAB,
                "A,P,Tableta,0,1,",
                "B,P,Tableta,abc,1,"));

        assertEquals(0, r.creados());
        assertEquals(0, r.actualizados());
        assertEquals(2, r.errores().size());
        verify(productos, never()).saveAll(any());
    }

    @Test
    void rechazaSiLaBoticaNoEstaAprobada() {
        conBotica("PENDIENTE");

        ApiException ex = assertThrows(ApiException.class, () -> service.cargar(10L, csv(CAB, "A,P,Tableta,1,1,")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(productos);
    }

    @Test
    void rechazaSiNoTieneBotica() {
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.cargar(10L, csv(CAB, "A,P,Tableta,1,1,")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(productos);
    }

    @Test
    void propagaElErrorDeUnArchivoInvalidoSinTocarLaBase() {
        conBotica("APROBADO");

        ApiException ex = assertThrows(ApiException.class, () -> service.cargar(10L, new byte[0]));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(productos);
    }

    // ---------- H59: alertas cuando baja el precio ----------

    @Test
    void registraLaBajaSoloDeLosExistentesQueBajanDePrecio() {
        conBotica("APROBADO");
        Producto panadol = producto(7L, "Panadol", "Tableta 500 mg x 10", "8.50", 40, 5);
        Producto ibuprofeno = producto(8L, "Ibuprofeno", "Tableta 400 mg x 10", "3.00", 40, 5);
        conCatalogo(panadol, ibuprofeno);

        service.cargar(10L, csv(CAB,
                "Panadol,Paracetamol,Tableta 500 mg x 10,7.90,40,",
                "Ibuprofeno,Ibuprofeno,Tableta 400 mg x 10,3.50,40,",
                "Nuevo,Principio,Caja,1.00,5,"));

        InOrder orden = inOrder(productos, alertas);
        orden.verify(productos).saveAll(any());
        orden.verify(productos).flush();
        orden.verify(alertas).registrarBaja(7L, new BigDecimal("8.50"), new BigDecimal("7.90"));
        verifyNoMoreInteractions(alertas);
    }

    @Test
    void sinBajasDePrecio_noRegistraAlertas() {
        conBotica("APROBADO");
        conCatalogo(producto(7L, "Panadol", "Tableta 500 mg x 10", "8.50", 40, 5));

        service.cargar(10L, csv(CAB, "Panadol,Paracetamol,Tableta 500 mg x 10,8.50,10,"));

        verifyNoInteractions(alertas);
        verify(productos, never()).flush();
    }

    @Test
    void variasBajas_publicanUnSoloEventoConTodasLasAlertas() {
        conBotica("APROBADO");
        conCatalogo(producto(7L, "Panadol", "Tableta 500 mg x 10", "8.50", 40, 5),
                producto(8L, "Ibuprofeno", "Tableta 400 mg x 10", "3.00", 40, 5));
        AlertaService.AlertaCreada a1 = new AlertaService.AlertaCreada(1L, 42L, 7L,
                new BigDecimal("8.50"), new BigDecimal("7.90"));
        AlertaService.AlertaCreada a2 = new AlertaService.AlertaCreada(2L, 42L, 8L,
                new BigDecimal("3.00"), new BigDecimal("2.50"));
        when(alertas.registrarBaja(7L, new BigDecimal("8.50"), new BigDecimal("7.90"))).thenReturn(List.of(a1));
        when(alertas.registrarBaja(8L, new BigDecimal("3.00"), new BigDecimal("2.50"))).thenReturn(List.of(a2));

        service.cargar(10L, csv(CAB,
                "Panadol,Paracetamol,Tableta 500 mg x 10,7.90,40,",
                "Ibuprofeno,Ibuprofeno,Tableta 400 mg x 10,2.50,40,"));

        ArgumentCaptor<AlertasCreadas> evento = ArgumentCaptor.forClass(AlertasCreadas.class);
        verify(eventos, times(1)).publishEvent(evento.capture());
        assertEquals(2, evento.getValue().alertas().size());
        assertTrue(evento.getValue().alertas().containsAll(List.of(a1, a2)));
    }
}