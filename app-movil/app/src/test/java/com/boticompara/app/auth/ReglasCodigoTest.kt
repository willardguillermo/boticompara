package com.boticompara.app.auth

import com.boticompara.app.ui.auth.codigo.ReglasCodigo
import com.boticompara.app.ui.auth.codigo.puntosCruz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasCodigoTest {

    private fun entrada(anterior: String, nuevo: String) = ReglasCodigo.aplicarEntrada(anterior, nuevo)

    @Test
    fun escribirUnDigitoAvanzaLaCasillaActiva() {
        assertEquals("1", entrada("", "1"))
        assertEquals("123", entrada("12", "123"))
        assertEquals(3, ReglasCodigo.casillaActiva("123"))
    }

    @Test
    fun ignoraLoQueNoEsUnDigito() {
        assertEquals("12", entrada("12", "12a"))
        assertEquals("12", entrada("12", "12 "))
        assertEquals("", entrada("", "-"))
    }

    @Test
    fun pegarElCodigoCompletoLoReparteEnLasCasillas() {
        assertEquals("483920", entrada("", "483920"))
        assertEquals("483920", entrada("", "Tu código es 483 920."))
        assertEquals(listOf('4', '8', '3', '9', '2', '0'), ReglasCodigo.casillas("483920"))
    }

    @Test
    fun pegarSobreLoQueYaHabiaReemplazaElCodigo() {
        // Tenía "12" escrito y pega el código del correo al final
        assertEquals("483920", entrada("12", "12483920"))
        // Ya estaba completo y pega otro código
        assertEquals("777111", entrada("483920", "483920777111"))
    }

    @Test
    fun nuncaPasaDeSeisDigitos() {
        assertEquals("123456", entrada("123456", "1234567"))
        assertEquals("1234", entrada("12", "1234"))
        assertEquals(ReglasCodigo.LARGO - 1, ReglasCodigo.casillaActiva("123456"))
    }

    @Test
    fun backspaceBorraElUltimoYLaCasillaActivaRetrocede() {
        assertEquals("12345", entrada("123456", "12345"))
        assertEquals(5, ReglasCodigo.casillaActiva("12345"))
        assertEquals("", entrada("1", ""))
        assertEquals(0, ReglasCodigo.casillaActiva(""))
    }

    @Test
    fun casillasVaciasSonNull() {
        assertEquals(listOf('1', '2', null, null, null, null), ReglasCodigo.casillas("12"))
    }

    @Test
    fun codigoCompletoSoloConSeisDigitos() {
        assertEquals("483920", ReglasCodigo.codigoCompleto("483920"))
        assertNull(ReglasCodigo.codigoCompleto("48392"))
        assertNull(ReglasCodigo.codigoCompleto(""))
    }

    @Test
    fun laCruzTiene45PuntosComoElLogo() {
        val puntos = puntosCruz(9, 3)
        assertEquals(45, puntos.size)
        assertEquals(45, puntos.map { it.columna to it.fila }.toSet().size)
        // Un solo punto en el centro (anillo 0) y las puntas de los brazos en el anillo 4
        assertEquals(listOf(4 to 4), puntos.filter { it.anillo == 0 }.map { it.columna to it.fila })
        assertEquals(4, puntos.maxOf { it.anillo })
        // Los brazos miden 3 puntos de ancho: en la fila 0 solo están las columnas 3, 4 y 5
        assertEquals(listOf(3, 4, 5), puntos.filter { it.fila == 0 }.map { it.columna })
        assertTrue(puntos.none { it.fila < 3 && it.columna < 3 })
    }
}
