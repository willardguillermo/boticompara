package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.AlertaResponse
import com.boticompara.app.data.repository.AlertaRepository
import com.boticompara.app.data.repository.ResultadoAccionAlerta
import com.boticompara.app.data.repository.ResultadoAlertas
import kotlinx.coroutines.launch

// ViewModel (MVVM) de la pantalla de alertas de precio (H59).
// Se crea una sola vez en MainActivity para que el número del ícono de la barra
// y la pantalla de Alertas compartan el mismo estado.
class AlertasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AlertaRepository(application)

    var alertas by mutableStateOf<List<AlertaResponse>>(emptyList())
        private set
    var noLeidas by mutableStateOf(0)   // número sobre el ícono de Alertas
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)   // deslizar para actualizar
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var mensajeAccion by mutableStateOf<String?>(null)   // aviso al marcar como leída
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    // H59: carga la lista de alertas no leídas. La carga grande solo se muestra si todavía no hay datos
    fun cargar() {
        viewModelScope.launch {
            isLoading = alertas.isEmpty()
            consultar()
            isLoading = false
        }
    }

    fun refrescar() {
        viewModelScope.launch {
            isRefreshing = true
            consultar()
            isRefreshing = false
        }
    }

    private suspend fun consultar() {
        when (val resultado = repository.obtenerNoLeidas()) {
            is ResultadoAlertas.Exito -> {
                alertas = resultado.alertas
                mensajeError = null
                repository.obtenerResumen()?.let { noLeidas = it }
            }
            ResultadoAlertas.SesionVencida -> sesionVencida = true
            ResultadoAlertas.SinConexion -> mensajeError = "No se pudo conectar con el servidor"
            ResultadoAlertas.ErrorServidor -> mensajeError = "No se pudieron cargar tus alertas"
        }
    }

    // H59: actualiza solo el número del ícono (se llama al cambiar de pantalla)
    fun actualizarResumen() {
        viewModelScope.launch {
            repository.obtenerResumen()?.let { noLeidas = it }
        }
    }

    // H59: marcar una alerta como leída; sale de la lista porque la lista solo muestra no leídas
    fun marcarLeida(id: Long) {
        viewModelScope.launch {
            when (repository.marcarLeida(id)) {
                ResultadoAccionAlerta.Exito -> {
                    alertas = alertas.filter { it.id != id }
                    noLeidas = (noLeidas - 1).coerceAtLeast(0)
                    mensajeAccion = "Alerta marcada como leída"
                }
                ResultadoAccionAlerta.SesionVencida -> sesionVencida = true
                ResultadoAccionAlerta.NoDisponible -> {
                    mensajeAccion = "Esa alerta ya no existe"
                    cargar()
                }
                ResultadoAccionAlerta.SinConexion -> mensajeAccion = "No se pudo conectar con el servidor"
                ResultadoAccionAlerta.ErrorServidor -> mensajeAccion = "No se pudo marcar la alerta"
            }
        }
    }

    // H59: marcar todas las alertas como leídas
    fun marcarTodasLeidas() {
        viewModelScope.launch {
            when (repository.marcarTodasLeidas()) {
                ResultadoAccionAlerta.Exito -> {
                    alertas = emptyList()
                    noLeidas = 0
                    mensajeAccion = "Todas las alertas quedaron como leídas"
                }
                ResultadoAccionAlerta.SesionVencida -> sesionVencida = true
                ResultadoAccionAlerta.SinConexion -> mensajeAccion = "No se pudo conectar con el servidor"
                else -> mensajeAccion = "No se pudieron marcar las alertas"
            }
        }
    }

    fun mensajeMostrado() {
        mensajeAccion = null
    }

    // Como este ViewModel vive en MainActivity, hay que apagar el aviso de sesión vencida
    // después de volver al login; si no, rebotaría al login en el siguiente inicio de sesión
    fun sesionVencidaAtendida() {
        sesionVencida = false
    }

    // Al cerrar sesión o volver al login no debe quedar nada del usuario anterior
    fun limpiar() {
        alertas = emptyList()
        noLeidas = 0
        mensajeError = null
        mensajeAccion = null
        sesionVencida = false
    }
}