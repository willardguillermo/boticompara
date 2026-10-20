package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.ProductoResponse
import com.boticompara.app.data.repository.ProductoRepository
import com.boticompara.app.data.repository.ResultadoBusqueda
import kotlinx.coroutines.launch

// ViewModel (MVVM) de la búsqueda: valida, llama al Repository y expone el estado a la vista
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductoRepository(application)

    var productos by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
    var searchType by mutableStateOf("nombre")   // "nombre" (H22) o "principio" (H23)

    fun buscarProductos() {
        if (searchQuery.trim().length < 2) {
            productos = emptyList()
            mensaje = "Escribe al menos 2 letras"
            return
        }
        viewModelScope.launch {
            isLoading = true
            mensaje = null
            when (val resultado = repository.buscar(searchQuery.trim(), searchType, "precio_asc")) {   // H24
                is ResultadoBusqueda.Exito -> {
                    productos = resultado.productos
                    if (productos.isEmpty()) mensaje = "No encontramos ese medicamento"
                }
                ResultadoBusqueda.SesionVencida -> sesionVencida = true
                ResultadoBusqueda.SinConexion -> {
                    productos = emptyList()
                    mensaje = "No se pudo conectar con el servidor"
                }
                ResultadoBusqueda.ErrorServidor -> {
                    productos = emptyList()
                    mensaje = "No se pudo realizar la búsqueda"
                }
            }
            isLoading = false
        }
    }
}