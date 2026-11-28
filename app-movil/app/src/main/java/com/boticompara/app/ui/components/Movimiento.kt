package com.boticompara.app.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * true si las animaciones del sistema están apagadas (Opciones de desarrollador o Accesibilidad →
 * escala de animación 0). Entonces la app no desliza ni mueve nada: cambia de estado sin transición.
 */
@Composable
fun animacionesApagadas(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
