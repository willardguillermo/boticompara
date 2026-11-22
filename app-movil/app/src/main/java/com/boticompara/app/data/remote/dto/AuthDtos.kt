package com.boticompara.app.data.remote.dto

data class LoginRequest(val correo: String, val password: String)
data class UsuarioResponse(val id: Long, val nombre: String, val correo: String, val rol: String)
data class AuthResponse(val token: String, val tipo: String, val usuario: UsuarioResponse)
data class RegistroRequest(
    val nombre: String,
    val correo: String,
    val password: String,          // mínimo 8 caracteres
    val telefono: String? = null,
    val rol: String = "COMPRADOR"
)
// H67: el token de Firebase que se obtiene después de elegir la cuenta de Google
data class GoogleAuthRequest(val idToken: String)
data class RecuperarRequest(val correo: String)
data class RestablecerRequest(val correo: String, val token: String, val password: String)
data class MensajeResponse(val mensaje: String)
