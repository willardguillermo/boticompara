package com.boticompara.app.data.remote.dto

// H70: contrato de POST /chat (docs/API.md, sección 8)
data class MensajeChatDto(
    val rol: String,    // "usuario" o "asistente"
    val texto: String   // 1 a 1000 caracteres
)

data class ChatRequest(
    val mensaje: String,                         // 1 a 500 caracteres
    val historial: List<MensajeChatDto>? = null, // máximo 10, del más antiguo al más reciente
    val lat: Double? = null,
    val lng: Double? = null
)

data class ProductoChatResponse(
    val productoId: Long,
    val nombreComercial: String,
    val principioActivo: String,
    val presentacion: String,
    val precio: Double,
    val stock: Int,
    val stockMinimo: Int?,
    val boticaId: Long,
    val boticaNombre: String,
    val boticaDistrito: String,
    val distanciaKm: Double?
)

data class ChatResponse(
    val respuesta: String,
    val productos: List<ProductoChatResponse> = emptyList(),
    val respaldo: Boolean = false,
    val ayuda: Boolean = false   // true cuando respondió el filtro de crisis
)