package com.boticompara.usuario.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.boticompara.usuario.entity.EstadoReserva;

class PedidoCorreoServiceTest {

    private PedidoCorreoService.DatosNuevaReserva datos() {
        return new PedidoCorreoService.DatosNuevaReserva("dueno@botica.com", "Rosa Quispe", "BC-7K2QXM",
                "Paracetamol Genérico", "Tableta 500 mg x 10", 2, new BigDecimal("2.20"),
                "Carlos R.", Instant.parse("2026-11-21T20:30:00Z"));
    }

    @Test
    void asuntoTraeCodigoProductoYCantidad() {
        assertEquals("Nueva reserva BC-7K2QXM: Paracetamol Genérico x 2",
                PedidoCorreoService.asuntoNuevaReserva(datos()));
    }

    @Test
    void cuerpoTraeElTotalConDosDecimales() {
        String cuerpo = PedidoCorreoService.cuerpoNuevaReserva(datos());

        assertTrue(cuerpo.contains("Total: S/ 4.40"), cuerpo);
        assertTrue(cuerpo.contains("Cantidad: 2"), cuerpo);
    }

    @Test
    void cuerpoLlevaElNombreDelCompradorYLaHoraDeLima() {
        String cuerpo = PedidoCorreoService.cuerpoNuevaReserva(datos());

        assertTrue(cuerpo.contains("Comprador: Carlos R."), cuerpo);
        // 20:30 UTC = 15:30 en Lima (UTC-5)
        assertTrue(cuerpo.contains("Vence el: 21/11/2026 15:30"), cuerpo);
    }

    @Test
    void cuerpoNoTraeDatosDeContactoDelComprador() {
        String cuerpo = PedidoCorreoService.cuerpoNuevaReserva(datos());

        assertFalse(cuerpo.contains("@"), "no debe salir ningún correo en el cuerpo");
        assertFalse(cuerpo.contains("telefono"), cuerpo);
    }

    private PedidoCorreoService.DatosCambioEstado cambio(EstadoReserva estado) {
        return new PedidoCorreoService.DatosCambioEstado("comprador@mail.com", "Carlos R.", "BC-7K2QXM",
                estado, "Paracetamol Genérico", "Tableta 500 mg x 10", 2, new BigDecimal("2.20"),
                Instant.parse("2026-11-21T20:30:00Z"), "No nos queda stock", "FarmaVida",
                "Jr. Trujillo 210", "Lurigancho-Chosica", "014567890");
    }

    @Test
    void asuntoDeListaYDeRechazadaSonDistintos() {
        assertEquals("Tu reserva BC-7K2QXM está lista para recoger",
                PedidoCorreoService.asuntoCambioEstado(cambio(EstadoReserva.LISTA)));
        assertEquals("Tu reserva BC-7K2QXM fue rechazada",
                PedidoCorreoService.asuntoCambioEstado(cambio(EstadoReserva.RECHAZADA)));
    }

    @Test
    void listaIncluyeElPlazoDeRecogerYNoElMotivo() {
        String cuerpo = PedidoCorreoService.cuerpoCambioEstado(cambio(EstadoReserva.LISTA));

        assertTrue(cuerpo.contains("Puedes recogerla hasta el 21/11/2026 15:30"), cuerpo);
        assertFalse(cuerpo.contains("Motivo:"), cuerpo);
    }

    @Test
    void rechazadaIncluyeElMotivoYNoElPlazo() {
        String cuerpo = PedidoCorreoService.cuerpoCambioEstado(cambio(EstadoReserva.RECHAZADA));

        assertTrue(cuerpo.contains("Motivo: No nos queda stock"), cuerpo);
        assertFalse(cuerpo.contains("Puedes recogerla"), cuerpo);
    }
}