package com.boticompara.app.auth

import com.boticompara.app.ui.auth.codigo.CuentaRegresiva
import com.boticompara.app.ui.auth.codigo.ESPERA_POR_DEFECTO
import com.boticompara.app.ui.auth.codigo.formatoReloj
import com.boticompara.app.ui.auth.codigo.segundosDeEspera
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CuentaRegresivaTest {

    private fun fechaHttp(ms: Long) = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }
        .format(ms)

    @Test
    fun retryAfterEnSegundos() {
        assertEquals(38, segundosDeEspera("38"))
        assertEquals(12, segundosDeEspera(" 12 "))
        assertEquals(900, segundosDeEspera("900"))
    }

    @Test
    fun retryAfterNuncaEsMenorQueUnSegundo() {
        assertEquals(1, segundosDeEspera("0"))
    }

    @Test
    fun sinRetryAfterOIlegibleUsaLaEsperaPorDefecto() {
        assertEquals(ESPERA_POR_DEFECTO, segundosDeEspera(null))
        assertEquals(ESPERA_POR_DEFECTO, segundosDeEspera(""))
        assertEquals(ESPERA_POR_DEFECTO, segundosDeEspera("pronto"))
        assertEquals(ESPERA_POR_DEFECTO, segundosDeEspera("-5"))
        assertEquals(15, segundosDeEspera("mañana", porDefecto = 15))
    }

    @Test
    fun retryAfterComoFechaHttp() {
        val ahora = 1_795_000_000_000L
        assertEquals(90, segundosDeEspera(fechaHttp(ahora + 90_000), ahoraMs = ahora))
        // Una fecha ya pasada espera lo mínimo
        assertEquals(1, segundosDeEspera(fechaHttp(ahora - 5_000), ahoraMs = ahora))
    }

    @Test
    fun formatoDelReloj() {
        assertEquals("00:38", formatoReloj(38))
        assertEquals("01:15", formatoReloj(75))
        assertEquals("00:00", formatoReloj(0))
        assertEquals("00:00", formatoReloj(-3))
        assertEquals("15:00", formatoReloj(900))
    }

    @Test
    fun cuentaHaciaAtrasCadaSegundo() = runTest {
        val espera = CuentaRegresiva(this)
        espera.iniciar(3)
        assertTrue(espera.activa)
        assertEquals(3, espera.segundos)
        avanzar(1_000)
        assertEquals(2, espera.segundos)
        avanzar(2_000)
        assertEquals(0, espera.segundos)
        assertFalse(espera.activa)
    }

    @Test
    fun iniciarDeNuevoReemplazaLaEsperaAnterior() = runTest {
        val espera = CuentaRegresiva(this)
        espera.iniciar(60)
        avanzar(10_000)
        espera.iniciar(5)
        assertEquals(5, espera.segundos)
        avanzar(5_000)
        assertEquals(0, espera.segundos)
        espera.iniciar(10)
        espera.detener()
        assertFalse(espera.activa)
    }
}
