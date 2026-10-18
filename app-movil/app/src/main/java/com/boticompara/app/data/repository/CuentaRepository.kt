package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.remote.RecuperarRequest
import com.boticompara.app.data.remote.RestablecerRequest
import com.boticompara.app.data.remote.RetrofitClient

// Resultado de las operaciones de cuenta, sin depender de Retrofit en el ViewModel
sealed interface ResultadoCuenta {
    data object Exito : ResultadoCuenta
    data object DatosInvalidos : ResultadoCuenta   // 400: código inválido, vencido o ya usado
    data object ErrorServidor : ResultadoCuenta    // cualquier otro código HTTP
    data object SinConexion : ResultadoCuenta      // no se pudo llegar al backend
}

// Repository (MVVM): única capa que habla con la API para las acciones de cuenta (H17, H19, H20)
class CuentaRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)

    // H17: POST /auth/recuperar (siempre responde 200, exista o no el correo)
    suspend fun solicitarCodigo(correo: String): ResultadoCuenta = ejecutar {
        api.recuperar(RecuperarRequest(correo)).code()
    }

    // H17: POST /auth/restablecer
    suspend fun restablecer(correo: String, codigo: String, password: String): ResultadoCuenta = ejecutar {
        api.restablecer(RestablecerRequest(correo, codigo, password)).code()
    }

    // Convierte el código HTTP (o la excepción) en un ResultadoCuenta
    private suspend fun ejecutar(llamada: suspend () -> Int): ResultadoCuenta =
        try {
            when (llamada()) {
                in 200..299 -> ResultadoCuenta.Exito
                400 -> ResultadoCuenta.DatosInvalidos
                else -> ResultadoCuenta.ErrorServidor
            }
        } catch (e: Exception) {
            ResultadoCuenta.SinConexion
        }
}