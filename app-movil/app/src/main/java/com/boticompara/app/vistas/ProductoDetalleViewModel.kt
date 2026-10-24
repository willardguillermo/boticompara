package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.AlternativasResponse
import com.boticompara.app.data.remote.ProductoDetalleResponse
import com.boticompara.app.data.repository.ProductoRepository
import com.boticompara.app.data.repository.ResultadoAlternativas
import com.boticompara.app.data.repository.ResultadoDetalle
import kotlinx.coroutines.launch

// ViewModel (MVVM) del detalle del producto (H28) y sus alternativas (H36, H37, H41)
class ProductoDetalleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductoRepository(application)

    var productoDetalle by mutableStateOf<ProductoDetalleResponse?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    // H36, H37, H41
    var alternativas by mutableStateOf<AlternativasResponse?>(null)
        private set
    var cargandoAlternativas by mutableStateOf(false)
        private set
    var errorAlternativas by mutableStateOf(false)
        private set

    fun cargarDetalle(productoId: Long) {
        viewModelScope.launch {
            isLoading = true
            mensajeError = null
            val resultado = repository.obtenerDetalle(productoId)
            isLoading = false
            when (resultado) {
                is ResultadoDetalle.Exito -> {
                    productoDetalle = resultado.producto
                    cargarAlternativas(productoId)   // el detalle ya se ve mientras llegan las alternativas
                }
                ResultadoDetalle.SesionVencida -> sesionVencida = true
                ResultadoDetalle.NoDisponible -> {
                    mensajeError = "Este producto ya no está disponible"
                }
                ResultadoDetalle.SinConexion -> {
                    mensajeError = "No se pudo conectar con el servidor"
                }
                ResultadoDetalle.ErrorServidor -> {
                    mensajeError = "No se pudo obtener el detalle del producto"
                }
            }
        }
    }

    // Si las alternativas fallan, el detalle se sigue viendo
    private suspend fun cargarAlternativas(productoId: Long) {
        cargandoAlternativas = true
        errorAlternativas = false
        when (val resultado = repository.obtenerAlternativas(productoId)) {
            is ResultadoAlternativas.Exito -> alternativas = resultado.datos
            else -> errorAlternativas = true
        }
        cargandoAlternativas = false
    }
}