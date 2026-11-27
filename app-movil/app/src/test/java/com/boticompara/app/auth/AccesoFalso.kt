package com.boticompara.app.auth

import com.boticompara.app.data.remote.dto.AuthResponse
import com.boticompara.app.data.remote.dto.MensajeResponse
import com.boticompara.app.data.remote.dto.RegistroRequest
import com.boticompara.app.data.remote.dto.TokenVerificacionResponse
import com.boticompara.app.data.repository.AccesoRepository
import com.boticompara.app.data.repository.ResultadoApi

/** Repositorio en memoria: cada respuesta se configura en la prueba y se anotan las llamadas. */
class AccesoFalso : AccesoRepository {
    val llamadas = mutableListOf<String>()

    val respuestasLogin = ArrayDeque<ResultadoApi<AuthResponse>>()
    var login: ResultadoApi<AuthResponse> = ResultadoApi.Exito(SESION)
    var pedirRegistro: ResultadoApi<MensajeResponse> = OK
    var verificarRegistro: ResultadoApi<TokenVerificacionResponse> = ResultadoApi.Exito(TokenVerificacionResponse(TOKEN))
    var registrar: ResultadoApi<AuthResponse> = ResultadoApi.Exito(SESION)
    var pedirVerificacion: ResultadoApi<MensajeResponse> = OK
    var confirmar: ResultadoApi<MensajeResponse> = OK
    var pedirRecuperacion: ResultadoApi<MensajeResponse> =
        ResultadoApi.Exito(MensajeResponse("Si el correo está registrado, te enviamos las instrucciones para restablecer tu contraseña"))
    var restablecer: ResultadoApi<MensajeResponse> = OK

    var sesionGuardada: AuthResponse? = null
    var ultimoRegistro: RegistroRequest? = null
    var ultimaPassword: String? = null
    var ultimoCodigo: String? = null

    override suspend fun login(correo: String, password: String): ResultadoApi<AuthResponse> {
        llamadas += "login:$correo"
        ultimaPassword = password
        return respuestasLogin.removeFirstOrNull() ?: login
    }

    override suspend fun guardarSesion(sesion: AuthResponse) {
        sesionGuardada = sesion
    }

    override suspend fun cerrarSesion() {
        sesionGuardada = null
    }

    override suspend fun pedirCodigoRegistro(correo: String): ResultadoApi<MensajeResponse> {
        llamadas += "registro/codigo:$correo"
        return pedirRegistro
    }

    override suspend fun verificarCodigoRegistro(correo: String, codigo: String): ResultadoApi<TokenVerificacionResponse> {
        llamadas += "registro/verificar:$correo"
        ultimoCodigo = codigo
        return verificarRegistro
    }

    override suspend fun registrar(request: RegistroRequest): ResultadoApi<AuthResponse> {
        llamadas += "registro"
        ultimoRegistro = request
        return registrar
    }

    override suspend fun pedirCodigoVerificacion(correo: String): ResultadoApi<MensajeResponse> {
        llamadas += "verificacion/codigo:$correo"
        return pedirVerificacion
    }

    override suspend fun confirmarVerificacion(correo: String, codigo: String): ResultadoApi<MensajeResponse> {
        llamadas += "verificacion/confirmar:$correo"
        ultimoCodigo = codigo
        return confirmar
    }

    override suspend fun pedirCodigoRecuperacion(correo: String): ResultadoApi<MensajeResponse> {
        llamadas += "recuperar:$correo"
        return pedirRecuperacion
    }

    override suspend fun restablecer(correo: String, codigo: String, password: String): ResultadoApi<MensajeResponse> {
        llamadas += "restablecer:$correo"
        ultimoCodigo = codigo
        ultimaPassword = password
        return restablecer
    }
}
