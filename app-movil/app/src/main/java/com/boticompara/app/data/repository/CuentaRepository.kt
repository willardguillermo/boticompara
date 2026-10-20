package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.RecuperarRequest
import com.boticompara.app.data.remote.RestablecerRequest
import com.boticompara.app.data.remote.RetrofitClient

// Resultado de las operaciones de cuenta, sin depender de Retrofit en el ViewModel
sealed interface ResultadoCuenta {
    data object Exito : ResultadoCuenta
    data object DatosInvalidos : ResultadoCuenta   // 400: código inválido, vencido o ya usado
    data object SesionVencida : ResultadoCuenta    // 401: token vencido o inválido
    data object ErrorServidor : ResultadoCuenta    // cualquier otro código HTTP
    data object SinConexion : ResultadoCuenta      // no se pudo llegar al backend
}

// Repository (MVVM): única capa que habla con la API para las acciones de cuenta (H17, H19, H20)
class CuentaRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)

    // H17: POST /auth/recuperar (siempre responde 200, exista o no el correo)
    suspend fun solicitarCodigo(correo: String): ResultadoCuenta = ejecutar {
        api.recuperar(RecuperarRequest(correo)).code()
    }

    // H17: POST /auth/restablecer
    suspend fun restablecer(correo: String, codigo: String, password: String): ResultadoCuenta = ejecutar {
        api.restablecer(RestablecerRequest(correo, codigo, password)).code()
    }

    // H19: DELETE /usuarios/me. Si sale bien (o el token ya no sirve) se borra la sesión guardada.
    suspend fun eliminarCuenta(): ResultadoCuenta {
        val resultado = ejecutar { api.eliminarCuenta(tokenManager.getBearer()).code() }
        if (resultado == ResultadoCuenta.Exito || resultado == ResultadoCuenta.SesionVencida) {
            tokenManager.clearSession()
        }
        return resultado
    }

    // Convierte el código HTTP (o la excepción) en un ResultadoCuenta
    private suspend fun ejecutar(llamada: suspend () -> Int): ResultadoCuenta =
        try {
            when (llamada()) {
                in 200..299 -> ResultadoCuenta.Exito
                400 -> ResultadoCuenta.DatosInvalidos
                401 -> ResultadoCuenta.SesionVencida
                else -> ResultadoCuenta.ErrorServidor
            }
        } catch (e: Exception) {
            ResultadoCuenta.SinConexion
        }
}