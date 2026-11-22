package com.boticompara.app.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

// H71: dictado y lectura en voz alta del asistente
class VozManager(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onListo()
        fun onParcial(texto: String)
        fun onResultado(texto: String)
        fun onErrorEscucha(mensaje: String)
        fun onHabla(hablando: Boolean)
    }

    private val appContext = context.applicationContext
    private val principal = Handler(Looper.getMainLooper())

    private var reconocedor: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var textoPendiente: String? = null
    private var ttsHablando = false
    private var iniciandoEscucha = false
    private var reintentoHecho = false
    private var idUtteranceActual: String? = null
    private var contadorUtterance = 0

    init {
        tts = TextToSpeech(appContext) { estado ->
            principal.post {
                if (estado == TextToSpeech.SUCCESS) {
                    tts?.let { configurarTts(it) }
                    ttsListo = true
                    textoPendiente?.let { pendiente ->
                        textoPendiente = null
                        hablar(pendiente)
                    }
                }
            }
        }
    }

    fun escuchar() {
        if (iniciandoEscucha) return
        iniciandoEscucha = true
        reintentoHecho = false

        val esperarTts = ttsHablando
        if (esperarTts) callar()

        reconocedor?.destroy()
        reconocedor = null

        principal.removeCallbacks(iniciarReconocedor)
        principal.postDelayed(
            iniciarReconocedor,
            if (esperarTts) ESPERA_TRAS_TTS_MS else 0L
        )
    }

    fun detenerEscucha() {
        principal.removeCallbacks(iniciarReconocedor)
        iniciandoEscucha = false
        reconocedor?.stopListening()
    }

    fun hablar(texto: String) {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return
        val ttsActual = tts
        if (!ttsListo || ttsActual == null) {
            textoPendiente = limpio
            return
        }
        textoPendiente = null
        ttsHablando = true
        contadorUtterance++
        val id = "${ID_UTTERANCE}_$contadorUtterance"
        idUtteranceActual = id
        ttsActual.speak(limpio, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun callar() {
        textoPendiente = null
        idUtteranceActual = null
        ttsHablando = false
        tts?.stop()
        listener.onHabla(false)
    }

    fun liberar() {
        principal.removeCallbacks(iniciarReconocedor)
        iniciandoEscucha = false
        reintentoHecho = false
        reconocedor?.destroy()
        reconocedor = null
        textoPendiente = null
        idUtteranceActual = null
        ttsHablando = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsListo = false
    }

    private val iniciarReconocedor = Runnable {
        if (!iniciandoEscucha) return@Runnable
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            iniciandoEscucha = false
            listener.onErrorEscucha("No se pudo reconocer la voz")
            return@Runnable
        }

        val nuevo = SpeechRecognizer.createSpeechRecognizer(appContext)
        nuevo.setRecognitionListener(escuchaListener)
        reconocedor = nuevo
        nuevo.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
        iniciandoEscucha = false
    }

    private fun reintentarEscucha() {
        reconocedor?.destroy()
        reconocedor = null
        principal.removeCallbacks(iniciarReconocedor)
        principal.postDelayed(iniciarReconocedor, ESPERA_REINTENTO_MS)
    }

    private fun esErrorReintentable(error: Int): Boolean =
        error == SpeechRecognizer.ERROR_AUDIO ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS

    private fun configurarTts(ttsActual: TextToSpeech) {
        val resultado = ttsActual.setLanguage(Locale.forLanguageTag("es-PE"))
        if (resultado == TextToSpeech.LANG_MISSING_DATA ||
            resultado == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            ttsActual.language = Locale.forLanguageTag("es")
        }
        ttsActual.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                principal.post {
                    if (utteranceId != idUtteranceActual) return@post
                    ttsHablando = true
                    listener.onHabla(true)
                }
            }

            override fun onDone(utteranceId: String?) {
                principal.post {
                    if (utteranceId != idUtteranceActual) return@post
                    idUtteranceActual = null
                    ttsHablando = false
                    listener.onHabla(false)
                }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                principal.post {
                    if (utteranceId != idUtteranceActual) return@post
                    idUtteranceActual = null
                    ttsHablando = false
                    listener.onHabla(false)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                principal.post {
                    if (utteranceId != idUtteranceActual) return@post
                    idUtteranceActual = null
                    ttsHablando = false
                    listener.onHabla(false)
                }
            }
        })
    }

    private val escuchaListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener.onListo()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            Log.w("VozManager", "Error de reconocimiento: $error")
            if (error == SpeechRecognizer.ERROR_CLIENT) return
            if (esErrorReintentable(error) && !reintentoHecho) {
                reintentoHecho = true
                iniciandoEscucha = true
                reintentarEscucha()
                return
            }
            iniciandoEscucha = false
            listener.onErrorEscucha(mensajeDeError(error))
        }

        override fun onResults(results: Bundle?) {
            val texto = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (texto.isNullOrEmpty()) {
                listener.onErrorEscucha("No te escuché, intenta de nuevo")
            } else {
                listener.onResultado(texto)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texto = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!texto.isNullOrBlank()) listener.onParcial(texto)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun mensajeDeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché, intenta de nuevo"

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso del micrófono"

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para reconocer la voz"

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocimiento de voz está ocupado"

        else -> "No se pudo reconocer la voz"
    }

    private companion object {
        const val ID_UTTERANCE = "boti_asistente"
        const val ESPERA_TRAS_TTS_MS = 300L
        const val ESPERA_REINTENTO_MS = 400L
    }
}
