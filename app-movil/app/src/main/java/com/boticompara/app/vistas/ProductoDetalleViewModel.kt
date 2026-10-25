package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.AlternativasResponse
import com.boticompara.app.data.remote.ProductoDetalleResponse
import com.boticompara.app.data.repository.FavoritoRepository
import com.boticompara.app.data.repository.ProductoRepository
import com.boticompara.app.data.repository.ResultadoAccionFavorito
import com.boticompara.app.data.repository.ResultadoAlternativas
import com.boticompara.app.data.repository.ResultadoDetalle
import com.boticompara.app.data.repository.ResultadoFavoritos
import kotlinx.coroutines.launch

// ViewModel (MVVM) del detalle del producto (H28), sus alternativas (H36, H37, H41) y el favorito (H43, H45)
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

    // H36, H37, H41
    var alternativas by mutableStateOf<AlternativasResponse?>(null)
        private set
    var cargandoAlternativas by mutableStateOf(false)
        private set
    var errorAlternativas by mutableStateOf(false)
        private set

    // H43, H45: favorito
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

    // Revisa si el producto ya está en los favoritos del usuario. Si falla, el corazón queda vacío
    private suspend fun consultarFavorito(productoId: Long) {
        val resultado = favoritoRepository.obtener()
        if (resultado is ResultadoFavoritos.Exito) {
            esFavorito = resultado.favoritos.any { it.productoId == productoId }
        }
    }

    // H43: guardar en favoritos / H45: quitar de favoritos
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