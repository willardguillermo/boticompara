package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.RetrofitClient
import com.boticompara.app.data.remote.dto.ChatRequest
import com.boticompara.app.data.remote.dto.ChatResponse
import com.boticompara.app.data.remote.dto.MensajeChatDto
import com.google.gson.JsonParser
import kotlin.coroutines.cancellation.CancellationException

// Resultado de enviar una pregunta al asistente (H70)
sealed interface ResultadoChat {
    data class Exito(val respuesta: ChatResponse) : ResultadoChat
    data object SesionVencida : ResultadoChat                       // 401: token vencido
    data class Rechazado(val mensaje: String) : ResultadoChat       // 400, 403, 429, 503, 504: mensaje del servidor
    data object ErrorServidor : ResultadoChat                       // cualquier otro código HTTP
    data object SinConexion : ResultadoChat                         // no se pudo llegar al backend
}

// Repository (MVVM): única capa que habla con el asistente de chat (H70)
class ChatRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)

    // POST /chat (H70)
    suspend fun enviar(mensaje: String, historial: List<MensajeChatDto>): ResultadoChat =
        try {
            val response = api.enviarMensajeChat(
                auth = tokenManager.getBearer(),
                request = ChatRequest(
                    mensaje = mensaje,
                    historial = historial.ifEmpty { null }
                )
            )
            when {
                response.isSuccessful -> response.body()
                    ?.let { ResultadoChat.Exito(it) }
                    ?: ResultadoChat.ErrorServidor
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoChat.SesionVencida
                }
                response.code() in CODIGOS_CON_MENSAJE -> ResultadoChat.Rechazado(
                    mensajeDelServidor(response.errorBody()?.string())
                        ?: "No pudimos responder tu pregunta. Intenta de nuevo"
                )
                else -> ResultadoChat.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoChat.SinConexion
        }

    // El backend responde { status, error, mensaje, timestamp }; los errores del chat
    // (429, 503, 504) ya traen el aviso del SAMU y la Línea 113
    private fun mensajeDelServidor(cuerpo: String?): String? =
        try {
            JsonParser().parse(cuerpo ?: "").asJsonObject.get("mensaje")?.asString
        } catch (e: Exception) {
            null
        }

    private companion object {
        val CODIGOS_CON_MENSAJE = listOf(400, 403, 429, 503, 504)
    }
}