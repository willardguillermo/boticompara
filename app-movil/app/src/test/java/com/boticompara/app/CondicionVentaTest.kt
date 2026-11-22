package com.boticompara.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.boticompara.app.ui.components.ColoresReceta
import com.boticompara.app.ui.components.ReglasCondicionVenta
import com.boticompara.app.ui.reservas.ColoresReserva
import com.boticompara.app.ui.reservas.TonoEstado
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

    // ---- Colores: la receta en morado, igual que en la web (--receta y --receta-fondo) ----

    @Test
    fun laRecetaUsaElMoradoDeLaWeb() {
        fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
        assertEquals("#F3E8FF", hex(ColoresReceta.fondo(modoOscuro = false)))
        assertEquals("#6B21A8", hex(ColoresReceta.contenido(modoOscuro = false)))
    }

    @Test
    fun elMoradoDeLaRecetaNoSeConfundeConNingunEstado() {
        listOf(false, true).forEach { oscuro ->
            TonoEstado.entries.forEach {
                assertTrue(ColoresReserva.de(it, oscuro).fondo != ColoresReceta.fondo(oscuro))
            }
        }
    }

    @Test
    fun laRecetaCumpleContrasteAA() {
        listOf(false, true).forEach { oscuro ->
            val ratio = contraste(ColoresReceta.fondo(oscuro), ColoresReceta.contenido(oscuro))
            assertTrue("oscuro=$oscuro: $ratio", ratio >= 4.5)
        }
    }
}
