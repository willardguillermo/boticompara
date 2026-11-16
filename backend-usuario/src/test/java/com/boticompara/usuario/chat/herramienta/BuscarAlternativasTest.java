package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ChatProducto;
import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.dto.AlternativaResponse;
import com.boticompara.usuario.dto.AlternativasResponse;
import com.boticompara.usuario.service.AlternativasService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** H68: buscar_alternativas usa la variante que no registra la consulta (H42). */
@ExtendWith(MockitoExtension.class)
class BuscarAlternativasTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ContextoChat CARLOS = new ContextoChat(4L, "COMPRADOR", -11.9365, -76.6972);

    @Mock
    AlternativasService alternativas;

    @InjectMocks
    BuscarAlternativas herramienta;

    private static AlternativaResponse alt(long id) {
        return new AlternativaResponse(id, "Producto " + id, "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal("2.20"), 10, 2L, "FarmaVida", "Jr. Trujillo 210", "Chosica", "EQUIVALENTE",
                new BigDecimal("12.0"));
    }

    @Test
    void usaLaVarianteSinRegistroYNuncaElMetodoQueRegistra() throws Exception {
        when(alternativas.alternativasSinRegistro(7L))
                .thenReturn(new AlternativasResponse(7L, false, List.of(alt(13))));

        ResultadoHerramienta r = herramienta.ejecutar(CARLOS, JSON.readTree("{\"productoId\":7}"));

        verify(alternativas, never()).alternativas(any(), any());
        assertEquals(1, r.paraModelo().get("total"));
        assertEquals(new ChatProducto(13L, "Producto 13", "Paracetamol", "Tableta 500 mg x 10",
                new BigDecimal("2.20"), 10, null, 2L, "FarmaVida", "Chosica", null), r.tarjetas().get(0));
        assertFalse(JSON.writeValueAsString(r.paraModelo()).contains("Trujillo"));
    }

    @Test
    void aceptaElIdComoNumeroConDecimalCero() throws Exception {
        when(alternativas.alternativasSinRegistro(7L)).thenReturn(new AlternativasResponse(7L, true, List.of()));

        ResultadoHerramienta r = herramienta.ejecutar(CARLOS, JSON.readTree("{\"productoId\":7.0}"));

        assertEquals(0, r.paraModelo().get("total"));
        assertTrue(r.tarjetas().isEmpty());
    }

    @Test
    void devuelveComoMaximo5() throws Exception {
        when(alternativas.alternativasSinRegistro(7L)).thenReturn(new AlternativasResponse(7L, false,
                LongStream.rangeClosed(1, 9).mapToObj(BuscarAlternativasTest::alt).toList()));

        ResultadoHerramienta r = herramienta.ejecutar(CARLOS, JSON.readTree("{\"productoId\":7}"));

        assertEquals(9, r.paraModelo().get("total"));
        assertEquals(5, r.tarjetas().size());
    }

    @Test
    void idInvalido_devuelveErrorSinConsultar() throws Exception {
        for (String json : List.of("{}", "{\"productoId\":\"7\"}", "{\"productoId\":0}", "{\"productoId\":-3}",
                "{\"productoId\":7.5}", "{\"productoId\":null}")) {
            assertTrue(herramienta.ejecutar(CARLOS, JSON.readTree(json)).paraModelo().containsKey("error"), json);
        }
        verifyNoInteractions(alternativas);
    }
}
