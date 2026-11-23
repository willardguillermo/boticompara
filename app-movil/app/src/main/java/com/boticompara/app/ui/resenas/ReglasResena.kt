package com.boticompara.app.ui.resenas

import com.boticompara.app.data.remote.dto.ResenaResponse
import com.boticompara.app.data.remote.dto.ResenasResponse
import java.util.Locale

object ReglasResena {
    const val MAX_COMENTARIO = 500
    const val TAMANO_PAGINA = 10

    fun estrellasValidas(estrellas: Int): Boolean = estrellas in 1..5

    fun comentarioValido(comentario: String): Boolean = comentario.trim().length <= MAX_COMENTARIO

    fun normalizarComentario(comentario: String): String? =
        comentario.trim().takeIf { it.isNotEmpty() }

    fun mensajeValidacion(estrellas: Int, comentario: String): String? =
        when {
            !estrellasValidas(estrellas) -> "Elige entre 1 y 5 estrellas"
            !comentarioValido(comentario) -> "El comentario no puede superar los 500 caracteres"
            else -> null
        }

    fun formatearPromedio(promedio: Double?): String? =
        promedio?.let { String.format(Locale.US, "%.1f", it) }

    fun textoCantidad(total: Int): String =
        if (total == 1) "1 reseña" else "$total reseñas"

    fun textoResumen(promedio: Double?, total: Int): String {
        val promedioTexto = formatearPromedio(promedio) ?: return "Sin reseñas"
        if (total <= 0) return "Sin reseñas"
        return "$promedioTexto · ${textoCantidad(total)}"
    }

    /** El backend siempre manda autor en la lista; si faltara, la tarjeta no queda vacía. */
    fun autorVisible(resena: ResenaResponse): String =
        resena.autor?.takeIf { it.isNotBlank() } ?: "Comprador"

    fun textoEstrellas(estrellas: Int): String = "$estrellas de 5 estrellas"

    fun estrellasDePromedio(promedio: Double?): Int =
        promedio?.let { Math.round(it).toInt().coerceIn(1, 5) } ?: 0

    fun hayMasPaginas(datos: ResenasResponse): Boolean = datos.page + 1 < datos.totalPages

    fun agregarPagina(actuales: List<ResenaResponse>, nuevas: List<ResenaResponse>): List<ResenaResponse> {
        val ids = actuales.map { it.id }.toSet()
        return actuales + nuevas.filterNot { it.id in ids }
    }
}
