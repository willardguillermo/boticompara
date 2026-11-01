package com.boticompara.app

import com.boticompara.app.data.local.Distritos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistritosTest {

    @Test
    fun tiene35DistritosSinRepetir() {
        assertEquals(35, Distritos.LISTA.size)
        assertEquals(35, Distritos.LISTA.map { it.nombre }.toSet().size)
    }

    @Test
    fun todasLasCoordenadasEstanEnLima() {
        Distritos.LISTA.forEach { distrito ->
            assertTrue(
                "Latitud fuera de Lima en ${distrito.nombre}",
                distrito.lat in -12.55..-11.70
            )
            assertTrue(
                "Longitud fuera de Lima en ${distrito.nombre}",
                distrito.lng in -77.20..-76.60
            )
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

    @Test
    fun filtraPorTextoParcial() {
        assertEquals(
            listOf("San Juan de Lurigancho", "San Juan de Miraflores"),
            Distritos.filtrar("san juan").map { it.nombre }
        )
        assertEquals(listOf("Santiago de Surco"), Distritos.filtrar("SURCO").map { it.nombre })
        assertEquals(35, Distritos.filtrar("   ").size)
        assertTrue(Distritos.filtrar("zzz").isEmpty())
    }
}