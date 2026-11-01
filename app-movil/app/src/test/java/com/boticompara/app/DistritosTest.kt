package com.boticompara.app

import com.boticompara.app.data.local.Distritos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** H50: la lista de distritos para la ubicación manual. */
class DistritosTest {

    @Test
    fun tiene35DistritosSinRepetir() {
        assertEquals(35, Distritos.LISTA.size)
        assertEquals(35, Distritos.LISTA.map { it.nombre }.toSet().size)
    }

    @Test
    fun todasLasCoordenadasEstanEnLima() {
        Distritos.LISTA.forEach {
            assertTrue("${it.nombre}: latitud fuera de Lima", it.lat in -12.55..-11.70)
            assertTrue("${it.nombre}: longitud fuera de Lima", it.lng in -77.20..-76.60)
        }
    }

    @Test
    fun buscaSinDistinguirMayusculasNiTildes() {
        assertEquals("Jesús María", Distritos.buscar("jesus maria")?.nombre)
        assertNotNull(Distritos.buscar("LURIGANCHO-CHOSICA"))
        assertEquals("Rímac", Distritos.buscar("    rimac ")?.nombre)
        assertNull(Distritos.buscar("Callao"))
        assertNull(Distritos.buscar(""))
    }
}