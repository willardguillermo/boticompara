package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.AlternativasService;
import com.boticompara.usuario.service.BoticaService;
import com.boticompara.usuario.service.BusquedaService;
import com.boticompara.usuario.service.CatalogoService;
import com.boticompara.usuario.service.MedicamentoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** H68: el rol del token decide qué herramientas existen para el modelo. */
@ExtendWith(MockitoExtension.class)
class RegistroHerramientasTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ContextoChat CARLOS = new ContextoChat(4L, "COMPRADOR", null, null);
    private static final ContextoChat ROSA = new ContextoChat(1L, "DUENO_BOTICA", null, null);

    @Mock
    BusquedaService busqueda;

    @Mock
    AlternativasService alternativas;

    @Mock
    BoticaService boticas;

    @Mock
    CatalogoService catalogo;

    @Mock
    MedicamentoService medicamentos;

    RegistroHerramientas registro;

    @BeforeEach
    void armar() {
        CatalogoPropio propio = new CatalogoPropio(boticas, catalogo);
        registro = new RegistroHerramientas(List.of(new BuscarPrecios(busqueda), new BuscarAlternativas(alternativas),
                new VerStockBajo(propio), new BuscarEnMiCatalogo(propio), new BuscarMedicamento(medicamentos)));
    }

    private List<String> nombres(String rol) {
        return registro.declaraciones(rol).stream().map(DeclaracionHerramienta::nombre).toList();
    }

    @Test
    void elCompradorSoloConoceLasDosHerramientasDeBusqueda() {
        assertEquals(List.of("buscar_precios", "buscar_alternativas"), nombres("COMPRADOR"));
    }

    @Test
    void elDuenoConoceLasCinco() {
        assertEquals(List.of("buscar_precios", "buscar_alternativas", "ver_stock_bajo", "buscar_en_mi_catalogo",
                "buscar_medicamento"), nombres("DUENO_BOTICA"));
    }

    @Test
    void unRolDesconocidoNoTieneHerramientas() {
        assertTrue(registro.declaraciones("ADMIN").isEmpty());
    }

    @Test
    void siElCompradorPideUnaHerramientaDelDueno_noSeEjecuta() throws Exception {
        for (String nombre : List.of("ver_stock_bajo", "buscar_en_mi_catalogo", "buscar_medicamento")) {
            ResultadoHerramienta r = registro.ejecutar(CARLOS, nombre, JSON.readTree("{\"texto\":\"amoxi\"}"));

            assertEquals(Map.of("error", "Herramienta no disponible"), r.paraModelo(), nombre);
        }
        verifyNoInteractions(boticas, catalogo, medicamentos);
    }

    @Test
    void unaHerramientaQueNoExiste_devuelveError() throws Exception {
        ResultadoHerramienta r = registro.ejecutar(ROSA, "borrar_producto", JSON.readTree("{}"));

        assertEquals(Map.of("error", "Herramienta no disponible"), r.paraModelo());
    }

    @Test
    void unErrorDeLaApiVuelveAlModeloComoResultado() throws Exception {
        when(alternativas.alternativasSinRegistro(999L)).thenThrow(ApiException.notFound("Producto no encontrado"));

        ResultadoHerramienta r = registro.ejecutar(CARLOS, "buscar_alternativas", JSON.readTree("{\"productoId\":999}"));

        assertEquals(Map.of("error", "Producto no encontrado"), r.paraModelo());
        assertTrue(r.tarjetas().isEmpty());
    }

    @Test
    void unFalloInesperadoNoRompeElPedidoNiMuestraElDetalle() throws Exception {
        when(busqueda.buscar("para", "nombre", "precio_asc", null, null, null))
                .thenThrow(new IllegalStateException("jdbc:postgresql://host-secreto"));

        ResultadoHerramienta r = registro.ejecutar(CARLOS, "buscar_precios", JSON.readTree("{\"texto\":\"para\"}"));

        assertEquals(Map.of("error", "No se pudo hacer la consulta en este momento"), r.paraModelo());
    }
}
