package com.boticompara.app.ui.auth

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.RetrofitClient
import com.boticompara.app.data.remote.dto.LoginRequest
import com.boticompara.app.data.remote.dto.RegistroRequest
import com.boticompara.app.data.repository.GoogleAuthRepository
import com.boticompara.app.data.repository.ResultadoGoogle
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = RetrofitClient.getInstance(application)
    private val tokenManager = TokenManager(application)
    private val googleRepository = GoogleAuthRepository(application)

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
            if (exito) onSuccess()
        }
    }

    // H67: iniciar sesión con Google. Necesita el contexto de la Activity para mostrar el selector de cuentas
    fun loginConGoogle(activityContext: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            var exito = false
            when (val resultado = googleRepository.iniciarSesion(activityContext)) {
                is ResultadoGoogle.Exito -> {
                    val sesion = resultado.sesion
                    tokenManager.saveSession(sesion.token, sesion.usuario.nombre, sesion.usuario.rol)
                    exito = true
                }
                ResultadoGoogle.Cancelado -> Unit   // cerró el selector: no es un error
                ResultadoGoogle.SinCuentaGoogle ->
                    errorMessage = "No hay una cuenta de Google en este celular. Agrega una en Ajustes e inténtalo de nuevo"
                is ResultadoGoogle.Rechazado -> errorMessage = resultado.mensaje
                ResultadoGoogle.ErrorGoogle -> errorMessage = "No se pudo iniciar sesión con Google. Inténtalo de nuevo"
                ResultadoGoogle.ErrorServidor -> errorMessage = "No se pudo iniciar sesión con Google"
                ResultadoGoogle.SinConexion -> errorMessage = "No se pudo conectar con el servidor"
            }
            isLoading = false
            if (exito) onSuccess()
        }
    }

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
            if (registrado) onSuccess()
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            tokenManager.clearSession()
            onDone()
        }
    }
}
