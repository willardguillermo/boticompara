package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Reserva;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H72: condicionVenta sale en las cinco respuestas, siempre como último campo, y null también se envía. */
class CondicionVentaJsonTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private static String ultimoCampo(JsonNode nodo) {
        List<String> campos = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(campos::add);
        return campos.get(campos.size() - 1);
    }

    private void verificar(Object respuesta, String esperado) {
        JsonNode nodo = json.valueToTree(respuesta);
        assertEquals("condicionVenta", ultimoCampo(nodo));
        assertTrue(nodo.has("condicionVenta"));
        if (esperado == null) {
            assertTrue(nodo.get("condicionVenta").isNull());
        } else {
            assertEquals(esperado, nodo.get("condicionVenta").asText());
        }
    }

    private static Reserva reserva() {
        Reserva r = new Reserva();
        r.setId(15L);
        r.setCodigo("BC-7K2QXM");
        r.setProductoId(9L);
        r.setCantidad(2);
        r.setPrecioUnitario(new BigDecimal("2.20"));
        r.setEstado(EstadoReserva.PENDIENTE);
        r.setVenceEn(Instant.now());
        return r;
    }

    @Test
    void busquedaDetalleYAlternativa() {
        verificar(new BusquedaResponse(9L, "Clonazepam", "Clonazepam", "Tableta", BigDecimal.ONE, 1, 2L,
                "Salud Total", "Av. 1", "Chosica", null, null, null, "RECETA_RETENIDA")
                .conDistancia(new BigDecimal("1.25")), "RECETA_RETENIDA");
        verificar(new ProductoDetalleResponse(9L, "Amoxicilina", "Amoxicilina", "Cápsula", BigDecimal.ONE, 1, 2L,
                "Salud Total", "Av. 1", "Chosica", null, "CON_RECETA"), "CON_RECETA");
        verificar(new AlternativaResponse(9L, "Paracetamol", "Paracetamol", "Tableta", BigDecimal.ONE, 1, 2L,
                "Salud Total", "Av. 1", "Chosica", "EQUIVALENTE", null, null)
                .conAhorro(new BigDecimal("10.0")), null);
    }

    @Test
    void reservaYPedido() {
        Producto p = new Producto();
        p.setStock(5);
        verificar(ReservaResponse.from(reserva(), p, null, "CON_RECETA"), "CON_RECETA");
        verificar(PedidoResponse.from(reserva(), p, "Carlos Ramírez", 3L, null), null);
    }
}
