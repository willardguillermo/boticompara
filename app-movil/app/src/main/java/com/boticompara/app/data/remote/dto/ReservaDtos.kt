package com.boticompara.app.data.remote.dto

// H72, H74: el usuario sale del token, solo se envían el producto y la cantidad (1 a 10)
data class ReservaRequest(val productoId: Long, val cantidad: Int)

// H72–H76: formato de una reserva (docs/API.md, sección 9). Fechas en UTC (ISO 8601)
data class ReservaResponse(
    val id: Long,
    val codigo: String,                    // H73: BC- y 6 caracteres
    val estado: String,                    // uno de los 7 estados de la sección 9
    val productoId: Long,
    val nombreComercial: String,
    val presentacion: String,
    val cantidad: Int,
    val precioUnitario: Double,            // precio al momento de reservar
    val total: Double,
    val boticaId: Long,
    val boticaNombre: String,
    val boticaDireccion: String,
    val boticaDistrito: String,
    val boticaTelefono: String? = null,    // null si la botica no lo registró
    val motivoRechazo: String? = null,     // solo en RECHAZADA
    val venceEn: String? = null,
    val creadoEn: String,
    val actualizadoEn: String? = null
)

// Formato de error común de la API: se usa para mostrar el mensaje del servidor (404, 409)
data class ErrorApiResponse(
    val status: Int? = null,
    val error: String? = null,
    val mensaje: String? = null
)
