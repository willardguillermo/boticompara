package com.boticompara.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.boticompara.app.ui.reservas.CantidadReserva
import com.boticompara.app.ui.reservas.EstadoReserva
import com.boticompara.app.ui.reservas.FechaLima
import com.boticompara.app.ui.reservas.ReglasReserva
import com.boticompara.app.ui.reservas.TonoEstado
import com.boticompara.app.ui.reservas.coloresDe
import com.boticompara.app.ui.reservas.formatearSoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ReservasTest {

    // ---- H74: cantidad de 1 a 10 ----

    @Test
    fun laCantidadVaDe1a10() {
        assertEquals(1, CantidadReserva.limitar(0, stock = 50))
        assertEquals(1, CantidadReserva.limitar(-3, stock = 50))
        assertEquals(10, CantidadReserva.limitar(11, stock = 50))
        assertEquals(7, CantidadReserva.limitar(7, stock = 50))
    }

    @Test
    fun losBotonesRespetanLosLimites() {
        assertFalse(CantidadReserva.puedeDisminuir(1))
        assertTrue(CantidadReserva.puedeDisminuir(2))
        assertTrue(CantidadReserva.puedeAumentar(9, stock = 50))
        assertFalse(CantidadReserva.puedeAumentar(10, stock = 50))
        assertEquals(10, CantidadReserva.aumentar(10, stock = 50))
        assertEquals(1, CantidadReserva.disminuir(1, stock = 50))
    }

    @Test
    fun laCantidadNoPasaDelStock() {
        assertEquals(3, CantidadReserva.maximaPara(3))
        assertEquals(3, CantidadReserva.limitar(8, stock = 3))
        assertFalse(CantidadReserva.puedeAumentar(3, stock = 3))
        assertEquals(1, CantidadReserva.maximaPara(0))
    }

    @Test
    fun elTotalTieneDosDecimales() {
        assertEquals(BigDecimal("4.40"), CantidadReserva.total(2.20, 2))
        assertEquals(BigDecimal("3.30"), CantidadReserva.total(1.10, 3))   // 1.1 × 3 no da 3.3000000000000003
        assertEquals(BigDecimal("125.00"), CantidadReserva.total(12.50, 10))
        assertEquals("S/ 4.40", formatearSoles(CantidadReserva.total(2.20, 2)))
        assertEquals("S/ 2.20", formatearSoles(2.2))
    }

    // ---- H76: nombres y colores de los estados ----

    @Test
    fun losSieteEstadosTienenNombreEnEspanol() {
        val esperados = mapOf(
            "PENDIENTE" to "Pendiente",
            "CONFIRMADA" to "Confirmada",
            "LISTA" to "Lista para recoger",
            "ENTREGADA" to "Entregada",
            "RECHAZADA" to "Rechazada",
            "CANCELADA" to "Cancelada",
            "VENCIDA" to "Vencida"
        )
        assertEquals(7, EstadoReserva.entries.size)
        esperados.forEach { (estado, nombre) -> assertEquals(nombre, EstadoReserva.nombreDe(estado)) }
        assertEquals("Lista para recoger", EstadoReserva.nombreDe("lista"))
        assertEquals("OTRO", EstadoReserva.nombreDe("OTRO"))
        assertNull(EstadoReserva.desde(null))
    }

    @Test
    fun cadaEstadoTieneElColorDeLaWeb() {
        // Hex de web-usuario/src/index.css (.estado-pedido-*): fondo y texto
        fun claro(estado: String) = coloresDe(EstadoReserva.desde(estado)?.tono, modoOscuro = false)
        fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

        val web = mapOf(
            "PENDIENTE" to ("#FEF3C7" to "#B45309"),
            "CONFIRMADA" to ("#DBEAFE" to "#1D4ED8"),
            "LISTA" to ("#CCFBF1" to "#115E59"),
            "ENTREGADA" to ("#DCFCE7" to "#15803D"),
            "RECHAZADA" to ("#FEE2E2" to "#B91C1C"),
            "CANCELADA" to ("#EEF2F1" to "#557069"),
            "VENCIDA" to ("#EEF2F1" to "#557069"),
            "DESCONOCIDO" to ("#EEF2F1" to "#557069")
        )
        web.forEach { (estado, colores) ->
            assertEquals("Fondo de $estado", colores.first, hex(claro(estado).fondo))
            assertEquals("Texto de $estado", colores.second, hex(claro(estado).contenido))
        }

        // Como en la web: 6 colores, Cancelada y Vencida comparten el gris
        assertEquals(6, EstadoReserva.entries.map { it.tono }.toSet().size)
        assertEquals(EstadoReserva.CANCELADA.tono, EstadoReserva.VENCIDA.tono)
        assertEquals(TonoEstado.ERROR, EstadoReserva.RECHAZADA.tono)
    }

    @Test
    fun cadaEtiquetaDeEstadoCumpleContrasteAA() {
        listOf(false, true).forEach { oscuro ->
            TonoEstado.entries.forEach { tono ->
                val c = coloresDe(tono, oscuro)
                val ratio = contraste(c.fondo, c.contenido)
                assertTrue("$tono oscuro=$oscuro: $ratio", ratio >= 4.5)
            }
        }
        // En modo oscuro cada estado conserva un color distinto
        assertEquals(6, TonoEstado.entries.map { coloresDe(it, modoOscuro = true).fondo }.toSet().size)
    }

    // ---- H73, H76: tarjeta del código según el estado ----

    @Test
    fun elTextoDeLaTarjetaDependeDelEstado() {
        listOf("PENDIENTE", "CONFIRMADA", "LISTA").forEach {
            assertEquals("Muestra este código en la botica", ReglasReserva.mensajeCodigo(it))
        }
        assertEquals("Esta reserva fue rechazada", ReglasReserva.mensajeCodigo("RECHAZADA"))
        assertEquals("Cancelaste esta reserva", ReglasReserva.mensajeCodigo("CANCELADA"))
        assertEquals("Esta reserva venció", ReglasReserva.mensajeCodigo("VENCIDA"))
        assertEquals("Ya recogiste este pedido", ReglasReserva.mensajeCodigo("ENTREGADA"))
        assertEquals("Esta reserva ya no está activa", ReglasReserva.mensajeCodigo("OTRO"))
    }

    @Test
    fun laTarjetaUsaElVerdeAzuladoSoloSiEstaActiva() {
        listOf(false, true).forEach { oscuro ->
            fun colores(estado: String) = coloresDe(ReglasReserva.tonoTarjetaCodigo(estado), oscuro)
            listOf("PENDIENTE", "CONFIRMADA", "LISTA").forEach {
                assertEquals(coloresDe(TonoEstado.PRIMARIO, oscuro), colores(it))
            }
            listOf("RECHAZADA", "CANCELADA", "VENCIDA", "ENTREGADA", "OTRO").forEach {
                assertEquals("Fondo neutro para $it", coloresDe(TonoEstado.NEUTRO, oscuro), colores(it))
            }
        }
    }

    // ---- H75: cuándo se puede cancelar ----

    private val ahora = FechaLima.aMilis("2026-11-12T20:00:00Z")!!

    @Test
    fun soloSeCancelaPendienteConfirmadaOLista() {
        val manana = "2026-11-13T15:30:00.123456Z"
        assertTrue(ReglasReserva.puedeCancelar("PENDIENTE", manana, ahora))
        assertTrue(ReglasReserva.puedeCancelar("CONFIRMADA", manana, ahora))
        assertTrue(ReglasReserva.puedeCancelar("LISTA", manana, ahora))
        listOf("ENTREGADA", "RECHAZADA", "CANCELADA", "VENCIDA", "OTRO", null).forEach {
            assertFalse("No debería cancelarse $it", ReglasReserva.puedeCancelar(it, manana, ahora))
        }
    }

    @Test
    fun noSeCancelaSiYaVencio() {
        assertFalse(ReglasReserva.puedeCancelar("PENDIENTE", "2026-11-12T19:59:59Z", ahora))
        assertFalse(ReglasReserva.puedeCancelar("LISTA", "2026-11-12T20:00:00Z", ahora))
        assertTrue(ReglasReserva.puedeCancelar("PENDIENTE", null, ahora))
    }

    // ---- H73, H76: fechas en hora de Lima ----

    @Test
    fun formateaEnHoraDeLima() {
        // 15:30 UTC = 10:30 en Lima (UTC−5, sin horario de verano)
        assertEquals("13/11/2026, 10:30 a. m.", FechaLima.formatear("2026-11-13T15:30:00.123456Z"))
        // 03:05 UTC del día 13 todavía es el día 12 en Lima
        assertEquals("12/11/2026, 10:05 p. m.", FechaLima.formatear("2026-11-13T03:05:00Z"))
        assertEquals("13/11/2026, 12:00 p. m.", FechaLima.formatear("2026-11-13T17:00:00Z"))
        assertEquals("13/11/2026, 12:00 a. m.", FechaLima.formatear("2026-11-13T05:00:00Z"))
    }

    @Test
    fun leeFraccionesYDesfases() {
        val base = FechaLima.aMilis("2026-11-13T15:30:00Z")!!
        assertEquals(base + 123, FechaLima.aMilis("2026-11-13T15:30:00.123456Z"))
        assertEquals(base + 500, FechaLima.aMilis("2026-11-13T15:30:00.5Z"))
        assertEquals(base, FechaLima.aMilis("2026-11-13T10:30:00-05:00"))
        assertNull(FechaLima.aMilis("ayer"))
        assertEquals("ayer", FechaLima.formatear("ayer"))
    }

    @Test
    fun ordenaDeLaMasRecienteALaMasAntigua() {
        val fechas = listOf("2026-11-10T10:00:00Z", "2026-11-12T15:30:00.123456Z", "2026-11-11T08:00:00Z")
        assertEquals(
            listOf("2026-11-12T15:30:00.123456Z", "2026-11-11T08:00:00Z", "2026-11-10T10:00:00Z"),
            ReglasReserva.ordenarRecientes(fechas) { it }
        )
    }
}

// Contraste WCAG 2.x entre dos colores opacos
internal fun contraste(a: Color, b: Color): Double {
    fun canal(v: Float): Double = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    fun lum(c: Color) = 0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)
    val (claro, oscuro) = listOf(lum(a), lum(b)).sortedDescending()
    return (claro + 0.05) / (oscuro + 0.05)
}
