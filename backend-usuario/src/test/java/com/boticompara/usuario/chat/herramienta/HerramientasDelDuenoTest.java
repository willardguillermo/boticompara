package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ChatProducto;
import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.dto.MedicamentoResponse;
import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.BoticaService;
import com.boticompara.usuario.service.CatalogoService;
import com.boticompara.usuario.service.MedicamentoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** H68: ver_stock_bajo, buscar_en_mi_catalogo y buscar_medicamento. La botica sale siempre del usuario del token. */
@ExtendWith(MockitoExtension.class)
class HerramientasDelDuenoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ContextoChat ROSA = new ContextoChat(1L, "DUENO_BOTICA", -11.9365, -76.6972);

    @Mock
    BoticaService boticas;

    @Mock
    CatalogoService catalogo;

    @Mock
    MedicamentoService medicamentos;

    VerStockBajo verStockBajo;
    BuscarEnMiCatalogo buscarEnMiCatalogo;
    BuscarMedicamento buscarMedicamento;

    @BeforeEach
    void armar() {
        CatalogoPropio propio = new CatalogoPropio(boticas, catalogo);
        verStockBajo = new VerStockBajo(propio);
        buscarEnMiCatalogo = new BuscarEnMiCatalogo(propio);
        buscarMedicamento = new BuscarMedicamento(medicamentos);
    }

    private void rosaTieneBotica() {
        Botica b = new Botica();
        b.setId(1L);
        b.setUsuarioId(1L);
        b.setNombreComercial("Botica Salud Total");
        b.setDistrito("Lurigancho-Chosica");
        b.setDireccion("Av. Lima 123");
        b.setRuc("20131312955");
        b.setLatitud(new BigDecimal("-11.940001"));
        b.setLongitud(new BigDecimal("-76.700002"));
        when(boticas.obtenerMia(1L)).thenReturn(b);
    }

    private static ProductoResponse producto(long id) {
        return new ProductoResponse(id, "Amoxicilina " + id, "Amoxicilina", "Cápsula 500 mg x 10",
                new BigDecimal("6.00"), 3, 5, null);
    }

    @Test
    void verStockBajo_usaElCatalogoDelUsuarioDelTokenConStockBajo() throws Exception {
        rosaTieneBotica();
        when(catalogo.listar(1L, null, true)).thenReturn(List.of(producto(4)));

        // Aunque el modelo invente un parámetro de botica, no existe forma de usarlo
        ResultadoHerramienta r = verStockBajo.ejecutar(ROSA, JSON.readTree("{\"boticaId\":2,\"usuarioId\":2}"));

        assertEquals(new ChatProducto(4L, "Amoxicilina 4", "Amoxicilina", "Cápsula 500 mg x 10",
                new BigDecimal("6.00"), 3, 5, 1L, "Botica Salud Total", "Lurigancho-Chosica", null),
                r.tarjetas().get(0));
        verify(boticas, never()).obtenerMia(2L);
        String json = JSON.writeValueAsString(r.paraModelo());
        assertEquals("{\"total\":1,\"resultados\":[{\"productoId\":4,\"nombre\":\"Amoxicilina 4\","
                + "\"principioActivo\":\"Amoxicilina\",\"presentacion\":\"Cápsula 500 mg x 10\",\"precio\":6.00,"
                + "\"stock\":3,\"botica\":\"Botica Salud Total\",\"distrito\":\"Lurigancho-Chosica\"}]}", json);
        assertFalse(json.contains("20131312955") || json.contains("Av. Lima") || json.contains("11.94"));
    }

    @Test
    void verStockBajo_devuelveComoMaximo5() {
        rosaTieneBotica();
        when(catalogo.listar(1L, null, true))
                .thenReturn(LongStream.rangeClosed(1, 7).mapToObj(HerramientasDelDuenoTest::producto).toList());

        ResultadoHerramienta r = verStockBajo.ejecutar(ROSA, null);

        assertEquals(7, r.paraModelo().get("total"));
        assertEquals(5, r.tarjetas().size());
    }

    @Test
    void buscarEnMiCatalogo_buscaElTextoSoloEnSuBotica() throws Exception {
        rosaTieneBotica();
        when(catalogo.listar(1L, "amoxi", false)).thenReturn(List.of(producto(4)));

        ResultadoHerramienta r = buscarEnMiCatalogo.ejecutar(ROSA, JSON.readTree("{\"texto\":\" amoxi \"}"));

        assertEquals(1, r.tarjetas().size());
        assertEquals(5, r.tarjetas().get(0).stockMinimo());
    }

    @Test
    void buscarEnMiCatalogo_sinTexto_devuelveErrorSinConsultar() throws Exception {
        ResultadoHerramienta r = buscarEnMiCatalogo.ejecutar(ROSA, JSON.readTree("{\"texto\":\"  \"}"));

        assertTrue(r.paraModelo().containsKey("error"));
        verifyNoInteractions(boticas, catalogo);
    }

    @Test
    void duenoSinBotica_recibeSinBoticaRegistrada() throws Exception {
        when(boticas.obtenerMia(1L)).thenThrow(ApiException.notFound("Aún no registraste tu botica"));

        assertEquals(Map.of("error", "sin botica registrada"), verStockBajo.ejecutar(ROSA, null).paraModelo());
        assertEquals(Map.of("error", "sin botica registrada"),
                buscarEnMiCatalogo.ejecutar(ROSA, JSON.readTree("{\"texto\":\"amoxi\"}")).paraModelo());
        verify(catalogo, never()).listar(anyLong(), any(), anyBoolean());
    }

    @Test
    void buscarMedicamento_noGeneraTarjetasYDevuelveComoMaximo5() throws Exception {
        when(medicamentos.buscar("amoxi")).thenReturn(LongStream.rangeClosed(1, 6)
                .mapToObj(i -> new MedicamentoResponse(i, "Amoxil " + i, "Amoxicilina", "Cápsula", "500 mg")).toList());

        ResultadoHerramienta r = buscarMedicamento.ejecutar(ROSA, JSON.readTree("{\"texto\":\"amoxi\"}"));

        assertTrue(r.tarjetas().isEmpty());
        assertEquals(6, r.paraModelo().get("total"));
        List<?> filas = (List<?>) r.paraModelo().get("resultados");
        assertEquals(5, filas.size());
        assertEquals(Map.of("nombre", "Amoxil 1", "principioActivo", "Amoxicilina", "presentacion", "Cápsula",
                "concentracion", "500 mg"), filas.get(0));
    }

    @Test
    void buscarMedicamento_textoCorto_devuelveErrorSinConsultar() throws Exception {
        assertTrue(buscarMedicamento.ejecutar(ROSA, JSON.readTree("{\"texto\":\"a\"}")).paraModelo()
                .containsKey("error"));
        verifyNoInteractions(medicamentos);
    }

    @Test
    void lasTresSonSoloDelDueno() {
        assertEquals(Set.of("DUENO_BOTICA"), verStockBajo.roles());
        assertEquals(Set.of("DUENO_BOTICA"), buscarEnMiCatalogo.roles());
        assertEquals(Set.of("DUENO_BOTICA"), buscarMedicamento.roles());
        assertNull(verStockBajo.declaracion().parametros());
    }
}
