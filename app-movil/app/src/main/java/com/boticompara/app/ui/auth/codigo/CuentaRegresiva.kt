package com.boticompara.app.ui.auth.codigo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

/** Segundos entre un código y el siguiente cuando el servidor no dice otra cosa (contrato: 60 s). */
const val ESPERA_POR_DEFECTO = 60

/**
 * H20: la espera para volver a pedir un código ("Reenviar en 00:38"). Vive en el ViewModel,
 * así sigue contando aunque la tarjeta se vuelva a dibujar (por ejemplo, al girar el celular).
 */
class CuentaRegresiva(private val scope: CoroutineScope) {
    var segundos by mutableIntStateOf(0)
        private set
    val activa: Boolean get() = segundos > 0
    private var trabajo: Job? = null

    fun iniciar(total: Int) {
        trabajo?.cancel()
        segundos = total.coerceAtLeast(0)
        if (segundos == 0) return
        trabajo = scope.launch {
            while (segundos > 0) {
                delay(1_000)
                segundos -= 1
            }
        }
    }

    fun detener() = iniciar(0)
}

/**
 * Segundos que hay que esperar según el header Retry-After de un 429: en segundos ("38") o como fecha HTTP
 * ("Wed, 26 Nov 2026 21:30:00 GMT"). Si no vino o no se puede leer, `porDefecto`. Nunca menos de 1.
 */
fun segundosDeEspera(
    retryAfter: String?,
    porDefecto: Int = ESPERA_POR_DEFECTO,
    ahoraMs: Long = System.currentTimeMillis()
): Int {
    val texto = retryAfter?.trim().orEmpty()
    if (texto.isEmpty()) return porDefecto
    if (texto.all { it in '0'..'9' }) {
        return (texto.toLongOrNull() ?: return porDefecto).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }
    val fecha = try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }.parse(texto)
    } catch (e: Exception) {
        null
    } ?: return porDefecto
    return ceil((fecha.time - ahoraMs) / 1000.0).toInt().coerceAtLeast(1)
}

/** 38 → "00:38"; 75 → "01:15" (el reloj de "Reenviar en 00:38"). */
fun formatoReloj(segundos: Int): String {
    val s = segundos.coerceAtLeast(0)
    return "%02d:%02d".format(Locale.US, s / 60, s % 60)
}
