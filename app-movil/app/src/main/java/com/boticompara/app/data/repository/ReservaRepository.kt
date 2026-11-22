package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.RetrofitClient
import com.boticompara.app.data.remote.dto.ErrorApiResponse
import com.boticompara.app.data.remote.dto.ReservaRequest
import com.boticompara.app.data.remote.dto.ReservaResponse
import com.google.gson.Gson
import retrofit2.Response
import kotlin.coroutines.cancellation.CancellationException

// Resultado de crear, consultar o cancelar una reserva (H72–H76)
sealed interface ResultadoReserva {
    data class Exito(val reserva: ReservaResponse) : ResultadoReserva
    data object SesionVencida : ResultadoReserva                     // 401: token vencido
    data class Rechazada(val mensaje: String, val codigoHttp: Int) : ResultadoReserva   // 400, 403, 404, 409: mensaje del servidor
    data object ErrorServidor : ResultadoReserva                     // cualquier otro código HTTP
    data object SinConexion : ResultadoReserva                       // no se pudo llegar al backend
}

// Resultado del historial de reservas (H76)
sealed interface ResultadoReservas {
    data class Exito(val reservas: List<ReservaResponse>) : ResultadoReservas
    data object SesionVencida : ResultadoReservas
    data object ErrorServidor : ResultadoReservas
    data object SinConexion : ResultadoReservas
}

// Repository (MVVM): única capa que habla con la API de reservas (H72–H76)
class ReservaRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)
    private val gson = Gson()

    // POST /reservas (H72, H73, H74)
    suspend fun crear(productoId: Long, cantidad: Int): ResultadoReserva =
        accion("No se pudo crear la reserva") {
            api.crearReserva(tokenManager.getBearer(), ReservaRequest(productoId, cantidad))
        }

    // GET /reservas/{id} (H76)
    suspend fun obtener(id: Long): ResultadoReserva =
        accion("No encontramos esa reserva") {
            api.obtenerReserva(tokenManager.getBearer(), id)
        }

    // PATCH /reservas/{id}/cancelar (H75)
    suspend fun cancelar(id: Long): ResultadoReserva =
        accion("No se pudo cancelar la reserva") {
            api.cancelarReserva(tokenManager.getBearer(), id)
        }

    // GET /reservas/mias (H76)
    suspend fun misReservas(): ResultadoReservas =
        try {
            val response = api.obtenerMisReservas(tokenManager.getBearer())
            when {
                response.isSuccessful -> ResultadoReservas.Exito(response.body() ?: emptyList())
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoReservas.SesionVencida
                }
                else -> ResultadoReservas.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoReservas.SinConexion
        }

    private suspend fun accion(
        mensajePorDefecto: String,
        llamada: suspend () -> Response<ReservaResponse>
    ): ResultadoReserva =
        try {
            val response = llamada()
            val cuerpo = response.body()
            when {
                response.isSuccessful && cuerpo != null -> ResultadoReserva.Exito(cuerpo)
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoReserva.SesionVencida
                }
                response.code() in CODIGOS_CON_MENSAJE ->
                    ResultadoReserva.Rechazada(mensajeDelServidor(response) ?: mensajePorDefecto, response.code())
                else -> ResultadoReserva.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoReserva.SinConexion
        }

    // Lee "mensaje" del formato de error de la API, por ejemplo
    // "Solo hay 2 unidades disponibles de este producto" (409)
    private fun mensajeDelServidor(response: Response<*>): String? =
        try {
            response.errorBody()?.string()
                ?.let { gson.fromJson(it, ErrorApiResponse::class.java) }
                ?.mensaje
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

    private companion object {
        val CODIGOS_CON_MENSAJE = setOf(400, 403, 404, 409)
    }
}
