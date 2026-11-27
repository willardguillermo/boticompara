package com.boticompara.app.auth

import com.boticompara.app.data.repository.ResultadoApi
import com.boticompara.app.ui.auth.codigo.FaseCodigo
import com.boticompara.app.ui.auth.codigo.VerificacionCodigoEstado
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificacionCodigoEstadoTest {

    private fun TestScope.estadoCon(codigo: String = "483920") =
        VerificacionCodigoEstado(this).also { it.cambiar(codigo) }

    @Test
    fun devuelveElCodigoUnaSolaVezAlCompletarse() = runTest {
        val estado = VerificacionCodigoEstado(this)
        assertNull(estado.cambiar("48392"))
        assertEquals("483920", estado.cambiar("483920"))
        assertNull(estado.cambiar("4839201"))   // el séptimo dígito se ignora
        assertEquals("483920", estado.codigo)
    }

    @Test
    fun codigoIncompletoNoSeEnviaYSacude() = runTest {
        val estado = estadoCon("123")
        var llamadas = 0
        estado.verificar(enviar = { llamadas++; ResultadoApi.Exito(Unit) }) {}
        runCurrent()
        assertEquals(0, llamadas)
        assertEquals("Escribe los 6 dígitos del código.", estado.errorCodigo)
        assertEquals(1, estado.sacudidas)
        assertEquals(FaseCodigo.ESCRIBIENDO, estado.fase)
    }

    @Test
    fun correctoEsperaAlMenos900msYLuegoFijaExitoYContinua() = runTest {
        val estado = estadoCon()
        var verificado: String? = null
        estado.verificar(enviar = { ResultadoApi.Exito("token:$it") }) { verificado = it }
        assertEquals(FaseCodigo.REUNIR, estado.fase)
        assertEquals("Verificando código", estado.anuncio)
        assertTrue(estado.ocupado)

        avanzar(899)
        assertEquals(FaseCodigo.REUNIR, estado.fase)   // la API respondió al instante, pero la cruz sigue
        avanzar(1)
        assertEquals(FaseCodigo.FIJA, estado.fase)
        avanzar(300)
        assertEquals(FaseCodigo.EXITO, estado.fase)
        assertEquals("Correo verificado", estado.anuncio)
        assertNull(verificado)
        avanzar(1_100)
        assertEquals("token:483920", verificado)
        assertTrue(estado.exito)
    }

    @Test
    fun mientrasVerificaNoCambiaElCodigo() = runTest {
        val estado = estadoCon()
        estado.verificar(enviar = { ResultadoApi.Exito(Unit) }) {}
        assertNull(estado.cambiar(""))
        assertEquals("483920", estado.codigo)
    }

    @Test
    fun codigoIncorrectoFallaVuelveYLimpiaLasCasillas() = runTest {
        val estado = estadoCon()
        estado.verificar<Unit>(enviar = { fallo(400, "El código no es válido o venció. Pide uno nuevo") }) {}
        avanzar(900)
        assertEquals(FaseCodigo.FALLA, estado.fase)
        avanzar(650)
        assertEquals(FaseCodigo.VOLVER, estado.fase)
        avanzar(600)
        assertEquals(FaseCodigo.ESCRIBIENDO, estado.fase)
        assertEquals("El código no es válido o venció. Pide uno nuevo.", estado.errorCodigo)
        assertEquals("", estado.codigo)
        assertEquals(1, estado.sacudidas)
        assertEquals("", estado.anuncio)
    }

    @Test
    fun otroErrorNoFallaLaCruzNiBorraElCodigo() = runTest {
        val estado = estadoCon()
        estado.verificar<Unit>(enviar = { ResultadoApi.SinConexion }) {}
        avanzar(900)
        assertEquals(FaseCodigo.VOLVER, estado.fase)
        avanzar(600)
        assertEquals(FaseCodigo.ESCRIBIENDO, estado.fase)
        assertEquals("No se pudo conectar con el servidor.", estado.errorGeneral)
        assertNull(estado.errorCodigo)
        assertEquals("483920", estado.codigo)
        assertEquals(0, estado.sacudidas)
    }

    @Test
    fun conLasAnimacionesApagadasLosTiemposSonCortos() = runTest {
        val estado = estadoCon()
        estado.movimientoReducido = true
        var listo = false
        estado.verificar(enviar = { ResultadoApi.Exito(Unit) }) { listo = true }
        avanzar(400)
        assertEquals(FaseCodigo.EXITO, estado.fase)   // sin la pausa de la cruz fija
        avanzar(700)
        assertTrue(listo)
    }

    @Test
    fun escribirDespuesDeUnErrorLoQuita() = runTest {
        val estado = estadoCon("12")
        estado.exigirCompleto()
        assertEquals("Escribe los 6 dígitos del código.", estado.errorCodigo)
        estado.cambiar("123")
        assertNull(estado.errorCodigo)
    }

    @Test
    fun reenviarLimpiaElCodigoYEspera60Segundos() = runTest {
        val estado = estadoCon("12")
        estado.reenviar("Te enviamos un código nuevo.") { OK }
        runCurrent()
        assertEquals("", estado.codigo)
        assertEquals("Te enviamos un código nuevo.", estado.mensaje)
        assertEquals(60, estado.espera.segundos)

        // Mientras corre la espera no se puede volver a pedir
        var otraVez = false
        estado.reenviar("x") { otraVez = true; OK }
        runCurrent()
        assertFalse(otraVez)
    }

    @Test
    fun reenviarCon429UsaElRetryAfter() = runTest {
        val estado = estadoCon("12")
        estado.reenviar("x") { fallo(429, "Espera un momento antes de pedir otro código", retryAfter = "38") }
        runCurrent()
        assertEquals("Espera un momento antes de pedir otro código.", estado.errorGeneral)
        assertEquals(38, estado.espera.segundos)
        assertEquals("12", estado.codigo)
        avanzar(38_000)
        assertFalse(estado.espera.activa)
    }

    @Test
    fun reenviarCon503MuestraElMensaje() = runTest {
        val estado = estadoCon()
        estado.reenviar("x") { fallo(503, "No pudimos enviar el correo. Intenta en unos minutos") }
        runCurrent()
        assertEquals("No pudimos enviar el correo. Intenta en unos minutos.", estado.errorGeneral)
        assertFalse(estado.espera.activa)
    }
}
