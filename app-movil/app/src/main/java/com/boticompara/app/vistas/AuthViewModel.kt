package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.LoginRequest
import com.boticompara.app.data.remote.RegistroRequest
import com.boticompara.app.data.remote.RetrofitClient
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = RetrofitClient.getInstance(application)
    private val tokenManager = TokenManager(application)

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    // H18
    fun login(correo: String, contrasena: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            var exito = false
            try {
                val response = apiService.login(LoginRequest(correo.trim(), contrasena))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    tokenManager.saveSession(body.token, body.usuario.nombre, body.usuario.rol)
                    exito = true
                } else {
                    errorMessage = "Correo o contraseña incorrectos"
                }
            } catch (e: Exception) {
                errorMessage = "No se pudo conectar con el servidor"
            } finally {
                isLoading = false
            }
            // Fuera del try: un error al navegar ya no se muestra como error de conexion
            if (exito) onSuccess()
        }
    }

    // H15: el registro NO devuelve token, así que después se inicia sesión automáticamente
    fun registrar(
        nombre: String,
        correo: String,
        contrasena: String,
        telefono: String? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            var registrado = false
            try {
                val response = apiService.registro(
                    RegistroRequest(nombre.trim(), correo.trim(), contrasena, telefono, "COMPRADOR")
                )
                when {
                    response.isSuccessful -> registrado = true
                    response.code() == 409 -> errorMessage = "Ese correo ya está registrado"
                    response.code() == 400 -> errorMessage = "Revisa los datos: la contraseña debe tener al menos 8 caracteres"
                    else -> errorMessage = "No se pudo completar el registro"
                }
            } catch (e: Exception) {
                errorMessage = "No se pudo conectar con el servidor"
            } finally {
                isLoading = false
            }
            // Fuera del try: el login automatico se lanza solo si el registro salio bien
            if (registrado) login(correo, contrasena, onSuccess)
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            tokenManager.clearSession()
            onDone()
        }
    }
}