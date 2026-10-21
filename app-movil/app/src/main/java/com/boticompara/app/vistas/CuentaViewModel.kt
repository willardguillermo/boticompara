package com.boticompara.app.vistas

import android.app.Application
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.repository.CuentaRepository
import com.boticompara.app.data.repository.ResultadoCuenta
import kotlinx.coroutines.launch

// ViewModel (MVVM) de las acciones de cuenta: valida, llama al Repository y expone el estado a la vista
class CuentaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CuentaRepository(application)

    var isLoading by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var restablecido by mutableStateOf(false)
        private set
    var cuentaEliminada by mutableStateOf(false)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    // H17: pide el código de 6 dígitos. Por seguridad el mensaje es el mismo exista o no el correo.
    fun solicitarCodigo(correo: String) {
        val limpio = correo.trim()
        if (!esCorreoValido(limpio)) {
            mensaje = null
            error = "Escribe un correo válido"
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            mensaje = null
            when (repository.solicitarCodigo(limpio)) {
                ResultadoCuenta.Exito -> mensaje = "Si el correo existe, te enviamos un código"
                ResultadoCuenta.SinConexion -> error = "No se pudo conectar con el servidor"
                else -> error = "No se pudo procesar la solicitud"
            }
            isLoading = false
        }
    }

    // H17: cambia la contraseña con el código de 6 dígitos
    fun restablecer(correo: String, codigo: String, password: String, confirmar: String) {
        mensaje = null
        val limpio = correo.trim()
        val problema = when {
            !esCorreoValido(limpio) -> "Escribe un correo válido"
            codigo.length != 6 || !codigo.all { it.isDigit() } -> "El código debe tener 6 dígitos"
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
            password != confirmar -> "Las contraseñas no coinciden"
            else -> null
        }
        if (problema != null) {
            error = problema
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            when (repository.restablecer(limpio, codigo, password)) {
                ResultadoCuenta.Exito -> {
                    restablecido = true
                    mensaje = "¡Listo! Tu contraseña fue actualizada"
                }
                ResultadoCuenta.DatosInvalidos -> error = "El código no es válido o ya venció. Pide uno nuevo."
                ResultadoCuenta.SinConexion -> error = "No se pudo conectar con el servidor"
                else -> error = "No se pudo restablecer la contraseña"
            }
            isLoading = false
        }
    }

    // H19: elimina la cuenta del usuario logueado y cierra la sesión
    // H19: elimina la cuenta del usuario logueado y cierra la sesión.
    // Si el token ya venció (401), la cuenta NO se eliminó: se avisa al usuario.
    fun eliminarCuenta() {
        viewModelScope.launch {
            isLoading = true
            error = null
            when (repository.eliminarCuenta()) {
                ResultadoCuenta.Exito -> cuentaEliminada = true
                ResultadoCuenta.SesionVencida -> sesionVencida = true
                ResultadoCuenta.SinConexion -> error = "No se pudo conectar con el servidor"
                else -> error = "No se pudo eliminar la cuenta"
            }
            isLoading = false
        }
    }

    private fun esCorreoValido(correo: String) = Patterns.EMAIL_ADDRESS.matcher(correo).matches()
}