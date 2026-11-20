package com.boticompara.app.ui.reservas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.dto.ReservaResponse
import com.boticompara.app.data.repository.ReservaRepository
import com.boticompara.app.data.repository.ResultadoReserva
import kotlinx.coroutines.launch

// H73, H75, H76: detalle de una reserva, con su código y la opción de cancelarla
class ReservaDetalleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReservaRepository(application)

    var reserva by mutableStateOf<ReservaResponse?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var cancelando by mutableStateOf(false)
        private set
    var mensajeAccion by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    // GET /reservas/{id} (H76)
    fun cargar(id: Long) {
        viewModelScope.launch {
            isLoading = reserva == null
            when (val resultado = repository.obtener(id)) {
                is ResultadoReserva.Exito -> {
                    reserva = resultado.reserva
                    mensajeError = null
                }
                ResultadoReserva.SesionVencida -> sesionVencida = true
                is ResultadoReserva.Rechazada -> mensajeError = resultado.mensaje   // 404
                ResultadoReserva.SinConexion -> mensajeError = "No se pudo conectar con el servidor"
                ResultadoReserva.ErrorServidor -> mensajeError = "No se pudo cargar la reserva"
            }
            isLoading = false
        }
    }

    fun puedeCancelar(ahoraMilis: Long = System.currentTimeMillis()): Boolean {
        val actual = reserva ?: return false
        return ReglasReserva.puedeCancelar(actual.estado, actual.venceEn, ahoraMilis)
    }

    // PATCH /reservas/{id}/cancelar (H75)
    fun cancelar() {
        val id = reserva?.id ?: return
        if (cancelando) return
        viewModelScope.launch {
            cancelando = true
            when (val resultado = repository.cancelar(id)) {
                is ResultadoReserva.Exito -> {
                    reserva = resultado.reserva
                    mensajeAccion = "Reserva cancelada"
                }
                ResultadoReserva.SesionVencida -> sesionVencida = true
                // 409: "No se puede cancelar una reserva ENTREGADA", "La reserva ya venció"
                is ResultadoReserva.Rechazada -> {
                    mensajeAccion = resultado.mensaje
                    recargar(id)
                }
                ResultadoReserva.SinConexion -> mensajeAccion = "No se pudo conectar con el servidor"
                ResultadoReserva.ErrorServidor -> mensajeAccion = "No se pudo cancelar la reserva"
            }
            cancelando = false
        }
    }

    // Tras un 409 el estado cambió en el servidor: se vuelve a leer para mostrarlo
    private suspend fun recargar(id: Long) {
        val resultado = repository.obtener(id)
        if (resultado is ResultadoReserva.Exito) reserva = resultado.reserva
    }

    fun mensajeMostrado() {
        mensajeAccion = null
    }
}
