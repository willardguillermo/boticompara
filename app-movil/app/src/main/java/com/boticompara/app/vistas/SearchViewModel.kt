package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.ProductoResponse
import com.boticompara.app.data.remote.RetrofitClient
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = RetrofitClient.getInstance(application)
    private val tokenManager = TokenManager(application)

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
            mensaje = "Escribe al menos 2 letras"
            return
        }
        viewModelScope.launch {
            isLoading = true
            mensaje = null
            try {
                val response = apiService.buscarProductos(
                    auth = tokenManager.getBearer(),
                    query = searchQuery.trim(),
                    tipo = searchType,
                    orden = "precio_asc"            // H24: del más barato al más caro
                )
                when {
                    response.isSuccessful -> {
                        productos = response.body() ?: emptyList()
                        if (productos.isEmpty()) mensaje = "No encontramos ese medicamento"
                    }
                    response.code() == 401 -> {
                        tokenManager.clearSession()
                        sesionVencida = true
                    }
                    else -> {
                        productos = emptyList()
                        mensaje = "No se pudo realizar la búsqueda"
                    }
                }
            } catch (e: Exception) {
                productos = emptyList()
                mensaje = "No se pudo conectar con el servidor"
            } finally {
                isLoading = false
            }
        }
    }
}