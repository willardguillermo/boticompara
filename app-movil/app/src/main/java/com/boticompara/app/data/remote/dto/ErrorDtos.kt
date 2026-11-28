package com.boticompara.app.data.remote.dto

// Formato de error de toda la API. `codigo` solo viene en algunos errores (hoy, CORREO_NO_VERIFICADO del login).
data class ErrorResponse(
    val status: Int? = null,
    val error: String? = null,
    val mensaje: String? = null,
    val codigo: String? = null
)
