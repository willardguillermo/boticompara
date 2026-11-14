package com.boticompara.app.ui.favoritos

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.dto.FavoritoResponse
import com.boticompara.app.data.repository.FavoritoRepository
import com.boticompara.app.data.repository.ResultadoAccionFavorito
import com.boticompara.app.data.repository.ResultadoFavoritos
import kotlinx.coroutines.launch

class FavoritosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FavoritoRepository(application)

    var favoritos by mutableStateOf<List<FavoritoResponse>>(emptyList())
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
            isLoading = favoritos.isEmpty()
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
        when (val resultado = repository.obtener()) {
            is ResultadoFavoritos.Exito -> {
                favoritos = resultado.favoritos
                mensajeError = null
            }
            ResultadoFavoritos.SesionVencida -> sesionVencida = true
            ResultadoFavoritos.SinConexion -> mensajeError = "No se pudo conectar con el servidor"
            ResultadoFavoritos.ErrorServidor -> mensajeError = "No se pudieron cargar tus favoritos"
        }
    }

    fun quitar(productoId: Long) {
        viewModelScope.launch {
            when (repository.quitar(productoId)) {
                ResultadoAccionFavorito.Exito -> {
                    favoritos = favoritos.filter { it.productoId != productoId }
                    mensajeAccion = "Producto quitado de tus favoritos"
                }
                ResultadoAccionFavorito.SesionVencida -> sesionVencida = true
                ResultadoAccionFavorito.SinConexion -> mensajeAccion = "No se pudo conectar con el servidor"
                else -> mensajeAccion = "No se pudo quitar el producto"
            }
        }
    }

    fun fijarPrecioObjetivo(productoId: Long, precio: Double?) {
        viewModelScope.launch {
            when (repository.fijarPrecioObjetivo(productoId, precio)) {
                ResultadoAccionFavorito.Exito -> {
                    favoritos = favoritos.map {
                        if (it.productoId == productoId) it.copy(precioObjetivo = precio) else it
                    }
                    mensajeAccion =
                        if (precio == null) "Precio objetivo quitado" else "Precio objetivo guardado"
                }
                ResultadoAccionFavorito.SesionVencida -> sesionVencida = true
                ResultadoAccionFavorito.NoDisponible -> {
                    mensajeAccion = "El producto ya no está disponible"
                    cargar()
                }
                ResultadoAccionFavorito.SinConexion -> mensajeAccion = "No se pudo conectar con el servidor"
                ResultadoAccionFavorito.ErrorServidor -> mensajeAccion = "No se pudo guardar el precio objetivo"
            }
        }
    }

    fun mensajeMostrado() {
        mensajeAccion = null
    }
}
