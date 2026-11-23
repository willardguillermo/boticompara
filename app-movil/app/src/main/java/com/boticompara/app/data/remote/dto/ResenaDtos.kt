package com.boticompara.app.data.remote.dto

data class ResenaRequest(
    val estrellas: Int,
    val comentario: String? = null
)

/**
 * Sirve para los dos formatos de API.md (sección 11): el de la lista trae autor y no trae
 * boticaId ni oculta; el de PUT /boticas/{id}/resenas/mia y miResena es al revés.
 * Por eso esos tres campos son nulables: Gson deja null lo que no viene.
 */
data class ResenaResponse(
    val id: Long,
    val autor: String? = null,
    val estrellas: Int,
    val comentario: String? = null,
    val respuesta: String? = null,
    val respondidaEn: String? = null,
    val creadoEn: String,
    val actualizadoEn: String? = null,
    val oculta: Boolean? = null,
    val boticaId: Long? = null
)

data class ResenasResponse(
    val boticaId: Long,
    val promedio: Double?,
    val total: Int,
    val page: Int,
    val totalPages: Int,
    val resenas: List<ResenaResponse> = emptyList(),
    val miResena: ResenaResponse? = null
)
