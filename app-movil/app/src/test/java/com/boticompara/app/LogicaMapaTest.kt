package com.boticompara.app

import com.boticompara.app.data.remote.dto.ProductoResponse
import com.boticompara.app.ui.busqueda.CENTRO_LIMA_PUNTO
import com.boticompara.app.ui.busqueda.MarcadorBotica
import com.boticompara.app.ui.busqueda.OrigenUbicacion
import com.boticompara.app.ui.busqueda.Punto
import com.boticompara.app.ui.busqueda.boticasMasBaratas
import com.boticompara.app.ui.busqueda.distanciaKm
import com.boticompara.app.ui.busqueda.elegirUbicacionInicial
import com.boticompara.app.ui.busqueda.formatoDistancia
import com.boticompara.app.ui.busqueda.formatoPrecio
import com.boticompara.app.ui.busqueda.limitesCentrados
import com.boticompara.app.ui.busqueda.puntosCercanos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// H48: la lógica del mapa de resultados
class LogicaMapaTest {

    private val miraflores = Punto(-12.1211, -77.0297)
    private val sanIsidro = Punto(-12.0977, -77.0365)
    private val callao = Punto(-12.0566, -77.1181)

    private fun marcador(boticaId: Long, precio: Double) = MarcadorBotica(
        producto = ProductoResponse(
            productoId = boticaId * 10,
            nombreComercial = "Panadol",
            principioActivo = "Paracetamol",
            presentacion = "Tableta 500 mg x 10",
            precio = precio,
            stock = 5,
            boticaId = boticaId,
            boticaNombre = "Botica $boticaId",
            boticaDireccion = "Av. Prueba 123",
            boticaDistrito = "Lima"
        ),
        lat = -12.12,
        lng = -77.03
    )

    // ---- Ubicación inicial ----

    @Test
    fun conGpsAbreEnElGpsAunqueHayaUbicacionManual() {
        val inicial = elegirUbicacionInicial(gps = miraflores, manual = callao)
        assertEquals(miraflores, inicial.punto)
        assertEquals(OrigenUbicacion.GPS, inicial.origen)
        assertTrue(inicial.esDelUsuario)
    }

    @Test
    fun sinGpsAbreEnLaUbicacionManualGuardada() {
        val inicial = elegirUbicacionInicial(gps = null, manual = callao)
        assertEquals(callao, inicial.punto)
        assertEquals(OrigenUbicacion.MANUAL, inicial.origen)
        assertTrue(inicial.esDelUsuario)
    }

    @Test
    fun sinNingunaUbicacionAbreEnLimaCentroSinPuntoAzul() {
        val inicial = elegirUbicacionInicial(gps = null, manual = null)
        assertEquals(CENTRO_LIMA_PUNTO, inicial.punto)
        assertEquals(OrigenUbicacion.POR_DEFECTO, inicial.origen)
        assertFalse(inicial.esDelUsuario)
    }

    // ---- El más barato ----

    @Test
    fun resaltaLaBoticaConElPrecioMasBajo() {
        val baratas = boticasMasBaratas(listOf(marcador(1, 12.50), marcador(2, 9.90), marcador(3, 15.00)))
        assertEquals(setOf(2L), baratas)
    }

    @Test
    fun siEmpatanEnElPrecioMasBajoSeResaltanTodas() {
        val baratas = boticasMasBaratas(listOf(marcador(1, 9.90), marcador(2, 9.90), marcador(3, 10.00)))
        assertEquals(setOf(1L, 2L), baratas)
    }

    @Test
    fun sinMarcadoresNoHayMasBarato() {
        assertTrue(boticasMasBaratas(emptyList()).isEmpty())
    }

    @Test
    fun conUnaSolaBoticaEsaEsLaMasBarata() {
        assertEquals(setOf(7L), boticasMasBaratas(listOf(marcador(7, 3.20))))
    }

    // ---- Formato del precio ----

    @Test
    fun elPrecioSiempreLlevaDosDecimales() {
        assertEquals("S/ 12.50", formatoPrecio(12.5))
        assertEquals("S/ 8.00", formatoPrecio(8.0))
        assertEquals("S/ 0.90", formatoPrecio(0.9))
    }

    @Test
    fun elPrecioSeRedondeaADosDecimalesConPunto() {
        assertEquals("S/ 12.46", formatoPrecio(12.456))
        assertEquals("S/ 1250.00", formatoPrecio(1250.0))
    }

    // ---- Distancia ----

    @Test
    fun laDistanciaMenorAUnKmVaEnMetros() {
        assertEquals("850 m", formatoDistancia(0.85))
        assertEquals("1 m", formatoDistancia(0.0))
    }

    @Test
    fun laDistanciaDesdeUnKmVaEnKmConUnDecimal() {
        assertEquals("1.0 km", formatoDistancia(1.0))
        assertEquals("2.7 km", formatoDistancia(2.73))
    }

    @Test
    fun calculaLaDistanciaEnLineaRecta() {
        // Miraflores - San Isidro: unos 2.7 km
        assertEquals(2.7, distanciaKm(miraflores, sanIsidro), 0.15)
        assertEquals(0.0, distanciaKm(miraflores, miraflores), 0.0001)
    }

    // ---- Boticas en la primera vista ----

    @Test
    fun laPrimeraVistaIncluyeLasBoticasCercanasYDejaFueraLasLejanas() {
        val vista = puntosCercanos(miraflores, listOf(sanIsidro, callao))
        assertEquals(listOf(sanIsidro), vista)
    }

    @Test
    fun siNingunaEstaCercaIncluyeLaMasCercana() {
        val lejos = Punto(-11.90, -77.05)
        val vista = puntosCercanos(miraflores, listOf(lejos, callao))
        assertEquals(listOf(callao), vista)
    }

    @Test
    fun losLimitesQuedanCentradosEnElUsuario() {
        val limites = limitesCentrados(miraflores, listOf(sanIsidro))!!
        val centroLat = (limites.first.lat + limites.second.lat) / 2
        val centroLng = (limites.first.lng + limites.second.lng) / 2
        assertEquals(miraflores.lat, centroLat, 0.000001)
        assertEquals(miraflores.lng, centroLng, 0.000001)
        assertTrue(sanIsidro.lat in limites.first.lat..limites.second.lat)
        assertTrue(sanIsidro.lng in limites.first.lng..limites.second.lng)
    }

    @Test
    fun sinBoticasNoHayLimitesYSeUsaElZoomFijo() {
        assertNull(limitesCentrados(miraflores, emptyList()))
    }
}
