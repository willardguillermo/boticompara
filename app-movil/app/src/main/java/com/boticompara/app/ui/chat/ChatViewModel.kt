package com.boticompara.app.ui.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.dto.MensajeChatDto
import com.boticompara.app.data.remote.dto.ProductoChatResponse
import com.boticompara.app.data.repository.ChatRepository
import com.boticompara.app.data.repository.ResultadoChat
import kotlinx.coroutines.launch

// Un mensaje de la conversación tal como se muestra en pantalla (H70)
data class MensajeChat(
    val id: Long,
    val esUsuario: Boolean,
    val texto: String,
    val productos: List<ProductoChatResponse> = emptyList(),
    val esError: Boolean = false,   // aviso de la app, no se reenvía en el historial
    val esAyuda: Boolean = false    // respuesta del filtro de crisis: se destaca
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)
    private var siguienteId = 0L

    val mensajes = mutableStateListOf<MensajeChat>()

    var texto by mutableStateOf("")
        private set
    var enviando by mutableStateOf(false)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    fun cambiarTexto(nuevo: String) {
        texto = nuevo.take(LIMITE_MENSAJE)
    }

    fun enviarEscrito() = enviar(texto)

    fun enviar(pregunta: String) {
        val mensaje = pregunta.trim()
        if (mensaje.isEmpty() || enviando) return

        val historial = armarHistorial()
        mensajes.add(MensajeChat(id = siguienteId++, esUsuario = true, texto = mensaje))
        texto = ""
        enviando = true

        viewModelScope.launch {
            when (val resultado = repository.enviar(mensaje, historial)) {
                is ResultadoChat.Exito -> agregarRespuesta(
                    texto = resultado.respuesta.respuesta,
                    productos = resultado.respuesta.productos,
                    esAyuda = resultado.respuesta.ayuda
                )
                ResultadoChat.SesionVencida -> sesionVencida = true
                is ResultadoChat.Rechazado -> agregarRespuesta(resultado.mensaje, esError = true)
                ResultadoChat.SinConexion -> agregarRespuesta(
                    "No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo",
                    esError = true
                )
                ResultadoChat.ErrorServidor -> agregarRespuesta(
                    "El asistente no pudo responder. Intenta de nuevo en un momento",
                    esError = true
                )
            }
            enviando = false
        }
    }

    fun nuevaConversacion() {
        if (enviando) return
        mensajes.clear()
        texto = ""
    }

    fun sesionVencidaAtendida() {
        sesionVencida = false
    }

    private fun agregarRespuesta(
        texto: String,
        productos: List<ProductoChatResponse> = emptyList(),
        esError: Boolean = false,
        esAyuda: Boolean = false
    ) {
        mensajes.add(
            MensajeChat(
                id = siguienteId++,
                esUsuario = false,
                texto = texto,
                productos = productos,
                esError = esError,
                esAyuda = esAyuda
            )
        )
    }

    // Los 10 últimos mensajes ya mostrados, sin los avisos de error de la app
    private fun armarHistorial(): List<MensajeChatDto> =
        mensajes
            .filter { !it.esError }
            .takeLast(MAX_HISTORIAL)
            .map {
                MensajeChatDto(
                    rol = if (it.esUsuario) "usuario" else "asistente",
                    texto = it.texto.take(LIMITE_TEXTO_HISTORIAL)
                )
            }

    private companion object {
        const val LIMITE_MENSAJE = 500
        const val LIMITE_TEXTO_HISTORIAL = 1000
        const val MAX_HISTORIAL = 10
    }
}