package com.boticompara.app.vistas

import android.app.Application
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.RecuperarRequest
import com.boticompara.app.data.remote.RetrofitClient
import kotlinx.coroutines.launch

// H17, H19 y H20: acciones sobre la cuenta del comprador
class CuentaViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.getInstance(application)

    var isLoading by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // H17: pide el código de 6 dígitos. Por seguridad el mensaje es el mismo exista o no el correo.
    fun solicitarCodigo(correo: String) {
        val limpio = correo.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(limpio).matches()) {
            mensaje = null
            error = "Escribe un correo válido"
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            mensaje = null
            try {
                val response = api.recuperar(RecuperarRequest(limpio))
                if (response.isSuccessful) {
                    mensaje = "Si el correo existe, te enviamos un código"
                } else {
                    error = "No se pudo procesar la solicitud"
                }
            } catch (e: Exception) {
                error = "No se pudo conectar con el servidor"
            } finally {
                isLoading = false
            }
        }
    }
}