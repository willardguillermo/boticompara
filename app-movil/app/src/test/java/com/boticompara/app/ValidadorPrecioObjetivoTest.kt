package com.boticompara.app

import com.boticompara.app.vistas.ResultadoPrecioObjetivo
import com.boticompara.app.vistas.ValidadorPrecioObjetivo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorPrecioObjetivoTest {

    private fun valor(texto: String): Double? =
        (ValidadorPrecioObjetivo.validar(texto) as? ResultadoPrecioObjetivo.Valido)?.valor

    @Test
    fun aceptaPreciosValidos() {
        assertEquals(7.5, valor("7.50")!!, 0.0001)
        assertEquals(7.5, valor("7,5")!!, 0.0001)
        assertEquals(10.0, valor(" 10 ")!!, 0.0001)
        assertEquals(7.5, valor("7.500")!!, 0.0001)
        assertEquals(0.01, valor("0.01")!!, 0.0001)
    }

    @Test
    fun rechazaVacioYTextoNoNumerico() {
        assertTrue(ValidadorPrecioObjetivo.validar("") is ResultadoPrecioObjetivo.Invalido)
        assertTrue(ValidadorPrecioObjetivo.validar("abc") is ResultadoPrecioObjetivo.Invalido)
        assertTrue(ValidadorPrecioObjetivo.validar("-5") is ResultadoPrecioObjetivo.Invalido)
        assertTrue(ValidadorPrecioObjetivo.validar("1e3") is ResultadoPrecioObjetivo.Invalido)
    }

    @Test
    fun rechazaCeroMasDeDosDecimalesYMaximo() {
        assertTrue(ValidadorPrecioObjetivo.validar("0") is ResultadoPrecioObjetivo.Invalido)
        assertTrue(ValidadorPrecioObjetivo.validar("7.555") is ResultadoPrecioObjetivo.Invalido)
        assertTrue(ValidadorPrecioObjetivo.validar("100000") is ResultadoPrecioObjetivo.Invalido)
        assertEquals(99999.99, valor("99999.99")!!, 0.0001)
    }
}