package com.boticompara.app.ui.reservas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.dto.ReservaResponse
import com.boticompara.app.data.repository.ReservaRepository
import com.boticompara.app.data.repository.ResultadoReservas
import kotlinx.coroutines.launch

// H76: historial de reservas del comprador
class MisReservasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReservaRepository(application)

    var reservas by mutableStateOf<List<ReservaResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    fun cargar() {
        viewModelScope.launch {
            isLoading = reservas.isEmpty()
            consultar()
            isLoading = false
        }
    }

    // Deslizar para actualizar
    fun refrescar() {
        viewModelScope.launch {
            isRefreshing = true
            consultar()
            isRefreshing = false
        }
    }

    // GET /reservas/mias (H76)
    private suspend fun consultar() {
        when (val resultado = repository.misReservas()) {
            is ResultadoReservas.Exito -> {
                reservas = ReglasReserva.ordenarRecientes(resultado.reservas) { it.creadoEn }
                mensajeError = null
            }
            ResultadoReservas.SesionVencida -> sesionVencida = true
            ResultadoReservas.SinConexion -> mensajeError = "No se pudo conectar con el servidor"
            ResultadoReservas.ErrorServidor -> mensajeError = "No se pudieron cargar tus reservas"
        }
    }
}
