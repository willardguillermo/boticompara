package com.boticompara.app

import com.boticompara.app.ui.components.ReglasCondicionVenta
import com.boticompara.app.ui.reservas.ReglasReserva
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// H72: condición de venta (docs/API.md, secciones 5 y 9)
class CondicionVentaTest {

    @Test
    fun textoDeLaEtiquetaSegunLaCondicion() {
        assertEquals("Requiere receta médica", ReglasCondicionVenta.etiqueta("CON_RECETA"))
        assertEquals("Receta retenida", ReglasCondicionVenta.etiqueta("RECETA_RETENIDA"))
        assertEquals("Receta retenida", ReglasCondicionVenta.etiqueta("receta_retenida"))
        assertNull(ReglasCondicionVenta.etiqueta("SIN_RECETA"))
        assertNull(ReglasCondicionVenta.etiqueta(null))
        assertNull(ReglasCondicionVenta.etiqueta("OTRA"))
    }

    @Test
    fun seReservaSiHayStockYNoEsRecetaRetenida() {
        assertTrue(ReglasCondicionVenta.sePuedeReservar(10, "SIN_RECETA"))
        assertTrue(ReglasCondicionVenta.sePuedeReservar(10, "CON_RECETA"))
        assertTrue(ReglasCondicionVenta.sePuedeReservar(10, null))
        assertFalse(ReglasCondicionVenta.sePuedeReservar(10, "RECETA_RETENIDA"))
        assertFalse(ReglasCondicionVenta.sePuedeReservar(0, "SIN_RECETA"))
        assertFalse(ReglasCondicionVenta.sePuedeReservar(0, null))
    }

    @Test
    fun elAvisoDeRecetaSoloEsParaConReceta() {
        assertTrue(ReglasCondicionVenta.pideReceta("CON_RECETA"))
        assertFalse(ReglasCondicionVenta.pideReceta("RECETA_RETENIDA"))
        assertFalse(ReglasCondicionVenta.pideReceta("SIN_RECETA"))
        assertFalse(ReglasCondicionVenta.pideReceta(null))
    }

    @Test
    fun elAvisoEnLaReservaSoloSiEstaActiva() {
        listOf("PENDIENTE", "CONFIRMADA", "LISTA").forEach {
            assertTrue(ReglasReserva.mostrarAvisoReceta(it, "CON_RECETA"))
            assertFalse(ReglasReserva.mostrarAvisoReceta(it, "SIN_RECETA"))
            assertFalse(ReglasReserva.mostrarAvisoReceta(it, null))
        }
        listOf("ENTREGADA", "RECHAZADA", "CANCELADA", "VENCIDA").forEach {
            assertFalse("Sin aviso en $it", ReglasReserva.mostrarAvisoReceta(it, "CON_RECETA"))
        }
    }

    @Test
    fun textosDeLosAvisos() {
        assertEquals(
            "Presenta tu receta médica al recoger; sin ella la botica no puede entregarlo",
            ReglasCondicionVenta.AVISO_CON_RECETA
        )
        assertEquals(
            "Este medicamento se vende con receta retenida: acércate a la botica con tu receta médica",
            ReglasCondicionVenta.AVISO_RECETA_RETENIDA
        )
    }
}
