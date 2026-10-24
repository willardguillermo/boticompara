package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.ProductoDetalleResponse
import com.boticompara.app.data.remote.ProductoResponse
import com.boticompara.app.data.remote.RetrofitClient
import kotlin.coroutines.cancellation.CancellationException

// Resultado de la búsqueda, sin depender de Retrofit en el ViewModel
sealed interface ResultadoBusqueda {
    data class Exito(val productos: List<ProductoResponse>) : ResultadoBusqueda
    data object SesionVencida : ResultadoBusqueda   // 401: token vencido
    data object ErrorServidor : ResultadoBusqueda   // cualquier otro código HTTP
    data object SinConexion : ResultadoBusqueda     // no se pudo llegar al backend
}

// Resultado del detalle del producto (H28)
sealed interface ResultadoDetalle {
    data class Exito(val producto: ProductoDetalleResponse) : ResultadoDetalle
    data object SesionVencida : ResultadoDetalle   // 401: token vencido
    data object NoDisponible : ResultadoDetalle    // 404: sin stock, dado de baja u oculto
    data object ErrorServidor : ResultadoDetalle   // cualquier otro código HTTP
    data object SinConexion : ResultadoDetalle     // no se pudo llegar al backend
}

// Repository (MVVM): única capa que habla con la API para la búsqueda de productos (H22–H27) y detalle (H28)
class ProductoRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)

    // GET /productos/buscar (H22–H24, H27)
    suspend fun buscar(
        query: String,
        tipo: String,
        orden: String = "precio_asc",
        lat: Double? = null,
        lng: Double? = null,
        radioKm: Double? = null
    ): ResultadoBusqueda =
        try {
            val response = api.buscarProductos(
                auth = tokenManager.getBearer(),
                query = query,
                tipo = tipo,
                orden = orden,
                lat = lat,
                lng = lng,
                radioKm = radioKm
            )
            when {
                response.isSuccessful -> ResultadoBusqueda.Exito(response.body() ?: emptyList())
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoBusqueda.SesionVencida
                }
                else -> ResultadoBusqueda.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e   // H26: la búsqueda se canceló porque el usuario siguió escribiendo, no es un error
        } catch (e: Exception) {
            ResultadoBusqueda.SinConexion
        }

    // GET /productos/{id} (H28)
    suspend fun obtenerDetalle(id: Long): ResultadoDetalle =
        try {
            val response = api.obtenerDetalleProducto(
                auth = tokenManager.getBearer(),
                id = id
            )
            when {
                response.isSuccessful && response.body() != null -> ResultadoDetalle.Exito(response.body()!!)
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoDetalle.SesionVencida
                }
                response.code() == 404 -> ResultadoDetalle.NoDisponible
                else -> ResultadoDetalle.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoDetalle.SinConexion
        }
}