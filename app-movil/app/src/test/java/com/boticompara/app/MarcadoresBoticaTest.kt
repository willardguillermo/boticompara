package com.boticompara.app

import com.boticompara.app.data.remote.ProductoResponse
import com.boticompara.app.vistas.agruparPorBotica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarcadoresBoticaTest {

    private fun producto(
        id: Long,
        boticaId: Long,
        precio: Double,
        lat: Double?,
        lng: Double?
    ) = ProductoResponse(
        productoId = id,
        nombreComercial = "Paracetamol Genérico",
        principioActivo = "Paracetamol",
        presentacion = "Tableta 500 mg x 10",
        precio = precio,
        stock = 10,
        boticaId = boticaId,
        boticaNombre = "Botica $boticaId",
        boticaDireccion = "Av. Prueba 123",
        boticaDistrito = "Lima",
        boticaLatitud = lat,
        boticaLongitud = lng
    )

    @Test
    fun noPoneMarcadorALasBoticasSinCoordenadas() {
        val resultado = agruparPorBotica(
            listOf(
                producto(1, 1, 2.50, null, null),
                producto(2, 2, 3.00, -12.12, null)
            )
        )
        assertTrue(resultado.isEmpty())
    }

    @Test
    fun poneUnSoloMarcadorPorBoticaConElProductoMasBarato() {
        val resultado = agruparPorBotica(
            listOf(
                producto(1, 1, 4.00, -12.12, -77.03),
                producto(2, 1, 2.50, -12.12, -77.03)
            )
        )
        assertEquals(1, resultado.size)
        assertEquals(2L, resultado[0].producto.productoId)
        assertEquals(2.50, resultado[0].producto.precio, 0.0001)
    }

    @Test
    fun poneUnMarcadorPorCadaBoticaConUbicacion() {
        val resultado = agruparPorBotica(
            listOf(
                producto(1, 1, 2.50, -12.12, -77.03),
                producto(2, 2, 3.00, -11.93, -76.69),
                producto(3, 3, 1.80, null, null)
            )
        )
        assertEquals(2, resultado.size)
        assertEquals(-11.93, resultado[1].lat, 0.0001)
    }
}