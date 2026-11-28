package com.boticompara.app.data.repository

import com.boticompara.app.data.remote.dto.ErrorResponse
import com.google.gson.Gson

/**
 * Resultado de una llamada a la API sin depender de Retrofit en el ViewModel (H17, H18, H20).
 * Fallo trae lo que el ViewModel necesita para decidir: el código HTTP, el `mensaje` del servidor,
 * el `codigo` (por ejemplo CORREO_NO_VERIFICADO) y el header Retry-After de los 429.
 */
sealed interface ResultadoApi<out T> {
    data class Exito<T>(val valor: T) : ResultadoApi<T>

    data class Fallo(
        val status: Int,
        val mensaje: String? = null,
        val codigo: String? = null,
        val retryAfter: String? = null
    ) : ResultadoApi<Nothing>

    data object SinConexion : ResultadoApi<Nothing>

    companion object {
        private val gson = Gson()

        /** Arma el resultado desde la respuesta HTTP: cuerpo si salió bien, o el JSON de error si no. */
        fun <T> desde(status: Int, cuerpo: T?, cuerpoError: String?, retryAfter: String?): ResultadoApi<T> {
            if (status in 200..299 && cuerpo != null) return Exito(cuerpo)
            val error = leerError(cuerpoError)
            return Fallo(
                status = status,
                mensaje = error?.mensaje?.takeIf { it.isNotBlank() },
                codigo = error?.codigo?.takeIf { it.isNotBlank() },
                retryAfter = retryAfter?.takeIf { it.isNotBlank() }
            )
        }

        private fun leerError(json: String?): ErrorResponse? =
            try {
                if (json.isNullOrBlank()) null else gson.fromJson(json, ErrorResponse::class.java)
            } catch (e: Exception) {
                null   // el cuerpo no era el JSON de error (por ejemplo, una página del proxy)
            }
    }
}
