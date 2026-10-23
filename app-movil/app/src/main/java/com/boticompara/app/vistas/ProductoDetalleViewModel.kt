package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.ProductoDetalleResponse
import com.boticompara.app.data.repository.ProductoRepository
import com.boticompara.app.data.repository.ResultadoDetalle
import kotlinx.coroutines.launch

// ViewModel (MVVM) para la Pantalla de Detalle del Producto (H28)
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

    fun cargarDetalle(productoId: Long) {
        viewModelScope.launch {
            isLoading = true
            mensajeError = null
            when (val resultado = repository.obtenerDetalle(productoId)) {
                is ResultadoDetalle.Exito -> {
                    productoDetalle = resultado.producto
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
            isLoading = false
        }
    }
}