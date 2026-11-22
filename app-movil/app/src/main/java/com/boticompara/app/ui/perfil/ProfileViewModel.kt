package com.boticompara.app.ui.perfil

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.RetrofitClient
import com.boticompara.app.data.remote.dto.NotificacionesRequest
import com.boticompara.app.data.remote.dto.PerfilRequest
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.getInstance(application)
    private val tokenManager = TokenManager(application)

    var nombre by mutableStateOf("")
    var telefono by mutableStateOf("")
    var direccion by mutableStateOf("")
    var correo by mutableStateOf("")
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    // H66: preferencia de alertas de baja de precio
    var notificacionesPrecio by mutableStateOf(true)
        private set
    var guardandoNotificaciones by mutableStateOf(false)
        private set
    var mensajeNotificaciones by mutableStateOf<String?>(null)
        private set

    init { cargarPerfil() }

    fun cargarPerfil() {
        viewModelScope.launch {
            isLoading = true
            try {
                val resp = api.obtenerPerfil(tokenManager.getBearer())
                val p = resp.body()
                if (resp.isSuccessful && p != null) {
                    nombre = p.nombre
                    correo = p.correo
                    telefono = p.telefono ?: ""
                    direccion = p.direccion ?: ""
                    notificacionesPrecio = p.notificacionesPrecio ?: true
                } else if (resp.code() == 401) {
                    tokenManager.clearSession(); sesionVencida = true
                }
            } catch (e: Exception) {
                mensaje = "No se pudo conectar con el servidor"
            } finally { isLoading = false }
        }
    }

    fun guardar() {
        if (nombre.isBlank()) { mensaje = "El nombre es obligatorio"; return }
        viewModelScope.launch {
            isLoading = true
            try {
                val resp = api.actualizarPerfil(
                    tokenManager.getBearer(),
                    PerfilRequest(nombre.trim(), telefono.ifBlank { null }, direccion.ifBlank { null })
                )
                mensaje = when {
                    resp.isSuccessful -> "¡Cambios guardados correctamente!"
                    resp.code() == 401 -> { tokenManager.clearSession(); sesionVencida = true; null }
                    else -> "No se pudieron guardar los cambios"
                }
            } catch (e: Exception) {
                mensaje = "No se pudo conectar con el servidor"
            } finally { isLoading = false }
        }
    }

    // H66: PUT /usuarios/me/notificaciones. Actualización optimista: el interruptor cambia al instante
    // y se bloquean nuevos toques mientras se guarda; solo se revierte si el servidor falla.
    fun cambiarNotificaciones(activar: Boolean) {
        if (guardandoNotificaciones) return
        val valorAnterior = notificacionesPrecio
        notificacionesPrecio = activar
        guardandoNotificaciones = true
        mensajeNotificaciones = null
        viewModelScope.launch {
            try {
                val resp = api.actualizarNotificaciones(
                    tokenManager.getBearer(),
                    NotificacionesRequest(activar)
                )
                when {
                    resp.isSuccessful -> Unit
                    resp.code() == 401 -> {
                        notificacionesPrecio = valorAnterior
                        tokenManager.clearSession(); sesionVencida = true
                    }
                    else -> {
                        notificacionesPrecio = valorAnterior
                        mensajeNotificaciones = "No se pudo cambiar la preferencia de alertas"
                    }
                }
            } catch (e: Exception) {
                notificacionesPrecio = valorAnterior
                mensajeNotificaciones = "No se pudo conectar con el servidor"
            } finally {
                guardandoNotificaciones = false
            }
        }
    }

    fun cerrarSesion(onDone: () -> Unit) {
        viewModelScope.launch { tokenManager.clearSession(); onDone() }
    }
}
