package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.ProductoResponse
import com.boticompara.app.data.remote.RetrofitClient

// Resultado de la búsqueda, sin depender de Retrofit en el ViewModel
sealed interface ResultadoBusqueda {
    data class Exito(val productos: List<ProductoResponse>) : ResultadoBusqueda
    data object SesionVencida : ResultadoBusqueda   // 401: token vencido
    data object ErrorServidor : ResultadoBusqueda   // cualquier otro código HTTP
    data object SinConexion : ResultadoBusqueda     // no se pudo llegar al backend
}

// Repository (MVVM): única capa que habla con la API para la búsqueda de productos (H22–H27)
class ProductoRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)

    // GET /productos/buscar
    suspend fun buscar(query: String, tipo: String, orden: String = "precio_asc"): ResultadoBusqueda =
        try {
            val response = api.buscarProductos(
                auth = tokenManager.getBearer(),
                query = query,
                tipo = tipo,
                orden = orden
            )
            when {
                response.isSuccessful -> ResultadoBusqueda.Exito(response.body() ?: emptyList())
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoBusqueda.SesionVencida
                }
                else -> ResultadoBusqueda.ErrorServidor
            }
        } catch (e: Exception) {
            ResultadoBusqueda.SinConexion
        }
}