package com.boticompara.app.ui.alertas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.dto.AlertaResponse
import com.boticompara.app.data.repository.AlertaRepository
import com.boticompara.app.data.repository.ResultadoAccionAlerta
import com.boticompara.app.data.repository.ResultadoAlertas
import kotlinx.coroutines.launch

class AlertasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AlertaRepository(application)

    var alertas by mutableStateOf<List<AlertaResponse>>(emptyList())
        private set
    var noLeidas by mutableStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var mensajeAccion by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

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

    fun actualizarResumen() {
        viewModelScope.launch {
            repository.obtenerResumen()?.let { noLeidas = it }
        }
    }

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

    fun sesionVencidaAtendida() {
        sesionVencida = false
    }

    fun limpiar() {
        alertas = emptyList()
        noLeidas = 0
        mensajeError = null
        mensajeAccion = null
        sesionVencida = false
    }
}
