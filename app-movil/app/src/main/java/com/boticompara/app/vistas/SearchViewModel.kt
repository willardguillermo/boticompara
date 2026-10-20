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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ViewModel (MVVM) de la búsqueda: valida, llama al Repository y expone el estado a la vista
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductoRepository(application)

    // H26: tiempo de espera desde la última tecla antes de buscar
    private val esperaDebounceMs = 500L
    private var busquedaJob: Job? = null

    var productos by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set
    var searchType by mutableStateOf("nombre")   // "nombre" (H22) o "principio" (H23)
        private set

    // H26: cada tecla reinicia la espera; solo se busca cuando el usuario deja de escribir
    fun onQueryChange(texto: String) {
        searchQuery = texto
        programarBusqueda(inmediata = false)
    }

    // Al cambiar el criterio se vuelve a buscar sin esperar
    fun onTipoChange(tipo: String) {
        if (tipo == searchType) return
        searchType = tipo
        programarBusqueda(inmediata = true)
    }

    // Tecla "Buscar" del teclado: busca al instante
    fun buscarProductos() {
        if (searchQuery.trim().length < 2) {
            busquedaJob?.cancel()
            productos = emptyList()
            isLoading = false
            mensaje = "Escribe al menos 2 letras"
            return
        }
        programarBusqueda(inmediata = true)
    }

    // Botón ✕ del campo
    fun limpiar() {
        busquedaJob?.cancel()
        searchQuery = ""
        productos = emptyList()
        mensaje = null
        isLoading = false
    }

    private fun programarBusqueda(inmediata: Boolean) {
        busquedaJob?.cancel()   // cancela la búsqueda anterior si todavía no terminó
        val query = searchQuery.trim()
        if (query.length < 2) {
            productos = emptyList()
            mensaje = null
            isLoading = false
            return
        }
        busquedaJob = viewModelScope.launch {
            if (!inmediata) delay(esperaDebounceMs)
            ejecutarBusqueda(query)
        }
    }

    private suspend fun ejecutarBusqueda(query: String) {
        isLoading = true
        mensaje = null
        when (val resultado = repository.buscar(query, searchType, "precio_asc")) {   // H24
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