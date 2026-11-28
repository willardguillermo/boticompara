package com.boticompara.app.auth

import com.boticompara.app.data.remote.dto.MensajeResponse
import com.boticompara.app.data.repository.ResultadoApi
import com.boticompara.app.ui.auth.conPuntoFinal
import com.boticompara.app.ui.auth.esCorreoValido
import com.boticompara.app.ui.auth.esErrorDelCodigo
import com.boticompara.app.ui.auth.esTelefonoValido
import com.boticompara.app.ui.auth.esTokenVencido
import com.boticompara.app.ui.auth.mencionaContrasena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultadoApiTest {

    @Test
    fun respuestaCorrectaEsExito() {
        val r = ResultadoApi.desde(200, MensajeResponse("ok"), null, null)
        assertEquals(ResultadoApi.Exito(MensajeResponse("ok")), r)
    }

    @Test
    fun leeMensajeYCodigoDelError() {
        val json = """{"status":403,"error":"Forbidden","mensaje":"Verifica tu correo para entrar","codigo":"CORREO_NO_VERIFICADO","timestamp":"2026-11-26T21:30:00Z"}"""
        val r = ResultadoApi.desde<MensajeResponse>(403, null, json, null)
        assertEquals(fallo(403, "Verifica tu correo para entrar", "CORREO_NO_VERIFICADO"), r)
    }

    @Test
    fun guardaElRetryAfterDelLimite() {
        val json = """{"status":429,"mensaje":"Espera un momento antes de pedir otro código"}"""
        val r = ResultadoApi.desde<MensajeResponse>(429, null, json, "38")
        assertEquals(fallo(429, "Espera un momento antes de pedir otro código", null, "38"), r)
    }

    @Test
    fun errorSinJsonNoRompe() {
        assertEquals(fallo(503), ResultadoApi.desde<MensajeResponse>(503, null, "<html>Bad gateway</html>", ""))
        assertEquals(fallo(500), ResultadoApi.desde<MensajeResponse>(500, null, null, null))
        assertEquals(fallo(400), ResultadoApi.desde<MensajeResponse>(400, null, """{"mensaje":"  ","codigo":""}""", null))
    }

    @Test
    fun exitoSinCuerpoEsFallo() {
        assertEquals(fallo(204), ResultadoApi.desde<MensajeResponse>(204, null, null, null))
    }

    @Test
    fun mensajesConPuntoFinal() {
        assertEquals("El correo ya está registrado.", conPuntoFinal("El correo ya está registrado"))
        assertEquals("Listo.", conPuntoFinal("Listo."))
        assertEquals("¿Seguro?", conPuntoFinal("¿Seguro?"))
        assertEquals("", conPuntoFinal("  "))
    }

    @Test
    fun reconoceLosErroresDelContrato() {
        assertTrue(esTokenVencido(fallo(400, "Tu verificación del correo venció. Vuelve a pedir un código")))
        assertFalse(esTokenVencido(fallo(409, "Tu verificación del correo venció")))
        assertFalse(esTokenVencido(fallo(400, "El nombre es obligatorio")))

        assertTrue(esErrorDelCodigo(fallo(400, "El código no es válido o venció. Pide uno nuevo")))
        assertFalse(esErrorDelCodigo(fallo(400, "La contraseña debe tener entre 8 y 72 caracteres")))
        assertTrue(mencionaContrasena(fallo(400, "La contraseña debe tener entre 8 y 72 caracteres")))
    }

    @Test
    fun validaCorreoYTelefono() {
        assertTrue(esCorreoValido(" carlos@boticompara.nissidev.dev "))
        assertFalse(esCorreoValido("carlos@"))
        assertFalse(esCorreoValido("carlos boticompara.dev"))
        assertTrue(esTelefonoValido(""))
        assertTrue(esTelefonoValido("954321098"))
        assertFalse(esTelefonoValido("95-43"))
    }

    @Test
    fun falloVacioNoTieneMensaje() {
        assertNull(fallo(400).mensaje)
    }
}
