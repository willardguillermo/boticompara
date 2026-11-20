package com.boticompara.app.ui.reservas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.dto.ReservaResponse
import com.boticompara.app.data.repository.ReservaRepository
import com.boticompara.app.data.repository.ResultadoReserva
import kotlinx.coroutines.launch

// H72, H74: estado de la hoja "Reservar" del detalle del producto
class ReservarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReservaRepository(application)

    var cantidad by mutableIntStateOf(CantidadReserva.MINIMA)
        private set
    var enviando by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var reservaCreada by mutableStateOf<ReservaResponse?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    fun iniciar() {
        cantidad = CantidadReserva.MINIMA
        mensajeError = null
    }

    fun aumentar(stock: Int) {
        cantidad = CantidadReserva.aumentar(cantidad, stock)
        mensajeError = null
    }

    fun disminuir(stock: Int) {
        cantidad = CantidadReserva.disminuir(cantidad, stock)
        mensajeError = null
    }

    // POST /reservas (H72, H73)
    fun reservar(productoId: Long) {
        if (enviando) return
        viewModelScope.launch {
            enviando = true
            mensajeError = null
            when (val resultado = repository.crear(productoId, cantidad)) {
                is ResultadoReserva.Exito -> reservaCreada = resultado.reserva
                ResultadoReserva.SesionVencida -> sesionVencida = true
                // 404 y 409: "Solo hay 2 unidades disponibles…", "Ya tienes una reserva activa…"
                is ResultadoReserva.Rechazada -> mensajeError = resultado.mensaje
                ResultadoReserva.SinConexion -> mensajeError = "No se pudo conectar con el servidor"
                ResultadoReserva.ErrorServidor -> mensajeError = "No se pudo crear la reserva. Inténtalo de nuevo."
            }
            enviando = false
        }
    }

    fun reservaCreadaMostrada() {
        reservaCreada = null
    }
}
