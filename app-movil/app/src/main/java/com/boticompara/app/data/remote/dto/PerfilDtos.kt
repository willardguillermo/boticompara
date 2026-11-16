package com.boticompara.app.data.remote.dto

data class PerfilResponse(
    val id: Long, val nombre: String, val correo: String,
    val telefono: String?, val direccion: String?, val rol: String,
    val notificacionesPrecio: Boolean? = null   // H66: alertas de baja de precio
)
data class PerfilRequest(val nombre: String, val telefono: String?, val direccion: String?)
data class NotificacionesRequest(val notificacionesPrecio: Boolean)
