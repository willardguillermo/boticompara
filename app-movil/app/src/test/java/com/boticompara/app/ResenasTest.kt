package com.boticompara.app

import com.boticompara.app.data.remote.dto.ResenaResponse
import com.boticompara.app.data.remote.dto.ResenasResponse
import com.boticompara.app.data.repository.ErroresResena
import com.boticompara.app.ui.resenas.ReglasResena
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResenasTest {

    private fun resena(id: Long) = ResenaResponse(
        id = id,
        autor = "Carlos R.",
        estrellas = 4,
        comentario = "Buen servicio",
        creadoEn = "2026-11-13T18:20:00Z"
    )

    private fun datos(
        promedio: Double? = 4.3,
        total: Int = 12,
        page: Int = 0,
        totalPages: Int = 2,
        resenas: List<ResenaResponse> = listOf(resena(1)),
        miResena: ResenaResponse? = null
    ) = ResenasResponse(
        boticaId = 2,
        promedio = promedio,
        total = total,
        page = page,
        totalPages = totalPages,
        resenas = resenas,
        miResena = miResena
    )

    @Test
    fun elPromedioNuloNoSeMuestra() {
        assertNull(ReglasResena.formatearPromedio(null))
        assertEquals("4.3", ReglasResena.formatearPromedio(4.3))
        assertEquals("4.0", ReglasResena.formatearPromedio(4.0))
        assertEquals("5.0", ReglasResena.formatearPromedio(5.0))
    }

    @Test
    fun elResumenDiceSinResenasCuandoNoHayPromedio() {
        assertEquals("Sin reseñas", ReglasResena.textoResumen(null, 0))
        assertEquals("Sin reseñas", ReglasResena.textoResumen(4.3, 0))
        assertEquals("4.5 · 12 reseñas", ReglasResena.textoResumen(4.5, 12))
        assertEquals("4.5 · 1 reseña", ReglasResena.textoResumen(4.5, 1))
    }

    @Test
    fun sinPromedioNoHayEstrellasQueMostrar() {
        assertEquals(0, ReglasResena.estrellasDePromedio(null))
        assertEquals(4, ReglasResena.estrellasDePromedio(4.3))
        assertEquals(5, ReglasResena.estrellasDePromedio(4.8))
        assertEquals(1, ReglasResena.estrellasDePromedio(0.4))
    }

    @Test
    fun lasEstrellasVanDe1a5() {
        (1..5).forEach { assertTrue(ReglasResena.estrellasValidas(it)) }
        listOf(-1, 0, 6, 100).forEach { assertFalse(ReglasResena.estrellasValidas(it)) }
        assertEquals("Elige entre 1 y 5 estrellas", ReglasResena.mensajeValidacion(0, "Hola"))
        assertNull(ReglasResena.mensajeValidacion(3, "Hola"))
    }

    @Test
    fun elComentarioTieneHasta500Caracteres() {
        assertTrue(ReglasResena.comentarioValido("A".repeat(500)))
        assertFalse(ReglasResena.comentarioValido("A".repeat(501)))
        assertEquals(
            "El comentario no puede superar los 500 caracteres",
            ReglasResena.mensajeValidacion(4, "A".repeat(501))
        )
    }

    @Test
    fun elComentarioVacioSeGuardaComoNull() {
        assertNull(ReglasResena.normalizarComentario("   "))
        assertNull(ReglasResena.normalizarComentario(""))
        assertEquals("Buen servicio", ReglasResena.normalizarComentario("  Buen servicio  "))
    }

    @Test
    fun losErroresConMensajeDelServidorSeMuestranTalCual() {
        val servidor = "Solo puedes reseñar boticas donde ya recogiste una reserva"
        listOf(400, 403, 404, 429).forEach {
            assertTrue(ErroresResena.codigosConMensaje.contains(it))
            assertEquals(servidor, ErroresResena.mensaje(it, servidor, "Error por defecto"))
        }
        listOf(401, 500, 503).forEach {
            assertFalse(ErroresResena.codigosConMensaje.contains(it))
            assertNull(ErroresResena.mensaje(it, servidor, "Error por defecto"))
        }
    }

    @Test
    fun siElServidorNoDaMensajeSeUsaUnoPorDefecto() {
        assertEquals("Error por defecto", ErroresResena.mensaje(403, null, "Error por defecto"))
        assertEquals("Error por defecto", ErroresResena.mensaje(403, "   ", "Error por defecto"))
    }

    @Test
    fun laPaginacionSigueMientrasQuedenPaginas() {
        assertTrue(ReglasResena.hayMasPaginas(datos()))
        assertFalse(ReglasResena.hayMasPaginas(datos(page = 1)))
        assertFalse(ReglasResena.hayMasPaginas(datos(totalPages = 0)))
    }

    @Test
    fun alAgregarUnaPaginaNoSeRepitenResenas() {
        val resultado = ReglasResena.agregarPagina(
            listOf(resena(1), resena(2)),
            listOf(resena(2), resena(3))
        )
        assertEquals(listOf(1L, 2L, 3L), resultado.map { it.id })
    }

    @Test
    fun laRespuestaDelPutSinAutorSeLeeSinFallar() {
        // Formato de PUT /boticas/{id}/resenas/mia (API.md, sección 11): no trae autor
        val json = """
            {"id":8,"boticaId":2,"estrellas":4,"comentario":"Me atendieron rápido",
             "respuesta":null,"respondidaEn":null,"oculta":false,
             "creadoEn":"2026-11-13T18:20:00.123456Z","actualizadoEn":"2026-11-13T18:20:00.123456Z"}
        """.trimIndent()
        val r = Gson().fromJson(json, ResenaResponse::class.java)
        assertNull(r.autor)
        assertEquals(2L, r.boticaId)
        assertEquals(false, r.oculta)
        assertEquals("Comprador", ReglasResena.autorVisible(r))
    }

    @Test
    fun miResenaSinAutorYLaListaConAutorSeLeenBien() {
        val json = """
            {"boticaId":2,"promedio":4.3,"total":1,"page":0,"totalPages":1,
             "resenas":[{"id":8,"autor":"Carlos R.","estrellas":4,"comentario":null,"respuesta":null,
                         "respondidaEn":null,"creadoEn":"2026-11-13T18:20:00Z","actualizadoEn":"2026-11-13T18:20:00Z"}],
             "miResena":{"id":8,"boticaId":2,"estrellas":4,"comentario":null,"respuesta":null,
                         "respondidaEn":null,"oculta":true,"creadoEn":"2026-11-13T18:20:00Z",
                         "actualizadoEn":"2026-11-13T18:20:00Z"}}
        """.trimIndent()
        val d = Gson().fromJson(json, ResenasResponse::class.java)
        assertEquals("Carlos R.", ReglasResena.autorVisible(d.resenas.first()))
        assertNull(d.miResena?.autor)
        assertEquals(true, d.miResena?.oculta)
    }

    @Test
    fun unAutorVacioNoDejaLaTarjetaSinNombre() {
        assertEquals("Comprador", ReglasResena.autorVisible(resena(1).copy(autor = "  ")))
        assertEquals("Carlos R.", ReglasResena.autorVisible(resena(1)))
    }
}
