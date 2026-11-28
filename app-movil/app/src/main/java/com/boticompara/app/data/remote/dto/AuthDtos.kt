package com.boticompara.app.data.remote.dto

data class LoginRequest(val correo: String, val password: String)
data class UsuarioResponse(val id: Long, val nombre: String, val correo: String, val rol: String)
data class AuthResponse(val token: String, val tipo: String, val usuario: UsuarioResponse)

// H20: la cuenta se crea solo con el correo ya verificado (tokenVerificacion de POST /auth/registro/verificar).
// El token vive en la memoria del ViewModel: nunca se guarda ni se escribe en el log.
data class RegistroRequest(
    val tokenVerificacion: String,
    val nombre: String,
    val correo: String,
    val password: String,          // de 8 a 72 caracteres
    val telefono: String? = null,
    val rol: String = "COMPRADOR"
) {
    override fun toString() = "RegistroRequest(correo=$correo, rol=$rol)"
}

// H67: el token de Firebase que se obtiene después de elegir la cuenta de Google
data class GoogleAuthRequest(val idToken: String)
data class RecuperarRequest(val correo: String)
data class RestablecerRequest(val correo: String, val token: String, val password: String) {
    override fun toString() = "RestablecerRequest(correo=$correo)"
}
data class MensajeResponse(val mensaje: String)

// H20: POST /auth/registro/codigo y /auth/verificacion/codigo
data class CorreoRequest(val correo: String)

// H20: POST /auth/registro/verificar y /auth/verificacion/confirmar
data class CodigoCorreoRequest(val correo: String, val codigo: String)

data class TokenVerificacionResponse(val tokenVerificacion: String) {
    override fun toString() = "TokenVerificacionResponse(***)"
}
