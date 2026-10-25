package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.FavoritoResponse
import com.boticompara.app.data.repository.FavoritoRepository
import com.boticompara.app.data.repository.ResultadoAccionFavorito
import com.boticompara.app.data.repository.ResultadoFavoritos
import kotlinx.coroutines.launch

// ViewModel (MVVM) de la pantalla de favoritos (H44, H45)
class FavoritosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FavoritoRepository(application)

    var favoritos by mutableStateOf<List<FavoritoResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)   // deslizar para actualizar
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var mensajeAccion by mutableStateOf<String?>(null)   // aviso al quitar un favorito
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    // H44: carga la lista. La carga grande solo se muestra si todavía no hay datos
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

    // H45: quitar un producto de los favoritos
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

    fun mensajeMostrado() {
        mensajeAccion = null
    }
}