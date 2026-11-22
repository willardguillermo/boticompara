package com.boticompara.app.data.remote.dto

data class ResenaRequest(
    val estrellas: Int,
    val comentario: String? = null
)

data class ResenaResponse(
    val id: Long,
    val autor: String,
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
