package com.boticompara.app.ui.detalle

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.dto.AlternativasResponse
import com.boticompara.app.data.remote.dto.ProductoDetalleResponse
import com.boticompara.app.data.repository.FavoritoRepository
import com.boticompara.app.data.repository.ProductoRepository
import com.boticompara.app.data.repository.ResultadoAccionFavorito
import com.boticompara.app.data.repository.ResultadoAlternativas
import com.boticompara.app.data.repository.ResultadoDetalle
import com.boticompara.app.data.repository.ResultadoFavoritos
import kotlinx.coroutines.launch

class ProductoDetalleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductoRepository(application)
    private val favoritoRepository = FavoritoRepository(application)

    var productoDetalle by mutableStateOf<ProductoDetalleResponse?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    var alternativas by mutableStateOf<AlternativasResponse?>(null)
        private set
    var cargandoAlternativas by mutableStateOf(false)
        private set
    var errorAlternativas by mutableStateOf(false)
        private set

    var esFavorito by mutableStateOf(false)
        private set
    var procesandoFavorito by mutableStateOf(false)
        private set
    var mensajeFavorito by mutableStateOf<String?>(null)
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
                    consultarFavorito(productoId)
                    cargarAlternativas(productoId)
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

    private suspend fun cargarAlternativas(productoId: Long) {
        cargandoAlternativas = true
        errorAlternativas = false
        when (val resultado = repository.obtenerAlternativas(productoId)) {
            is ResultadoAlternativas.Exito -> alternativas = resultado.datos
            else -> errorAlternativas = true
        }
        cargandoAlternativas = false
    }

    private suspend fun consultarFavorito(productoId: Long) {
        val resultado = favoritoRepository.obtener()
        if (resultado is ResultadoFavoritos.Exito) {
            esFavorito = resultado.favoritos.any { it.productoId == productoId }
        }
    }

    fun alternarFavorito() {
        val productoId = productoDetalle?.productoId ?: return
        if (procesandoFavorito) return
        viewModelScope.launch {
            procesandoFavorito = true
            val resultado = if (esFavorito) favoritoRepository.quitar(productoId)
            else favoritoRepository.agregar(productoId)
            when (resultado) {
                ResultadoAccionFavorito.Exito -> {
                    esFavorito = !esFavorito
                    mensajeFavorito = if (esFavorito) "Producto guardado en tus favoritos"
                    else "Producto quitado de tus favoritos"
                }
                ResultadoAccionFavorito.SesionVencida -> sesionVencida = true
                ResultadoAccionFavorito.NoDisponible -> {
                    mensajeFavorito = "Este producto ya no está disponible"
                }
                ResultadoAccionFavorito.SinConexion -> {
                    mensajeFavorito = "No se pudo conectar con el servidor"
                }
                ResultadoAccionFavorito.ErrorServidor -> {
                    mensajeFavorito = "No se pudo actualizar tus favoritos"
                }
            }
            procesandoFavorito = false
        }
    }

    fun mensajeFavoritoMostrado() {
        mensajeFavorito = null
    }
}
