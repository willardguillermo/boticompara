package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.FavoritoRequest
import com.boticompara.app.data.remote.FavoritoResponse
import com.boticompara.app.data.remote.RetrofitClient
import kotlin.coroutines.cancellation.CancellationException

// Resultado de consultar la lista de favoritos (H44)
sealed interface ResultadoFavoritos {
    data class Exito(val favoritos: List<FavoritoResponse>) : ResultadoFavoritos
    data object SesionVencida : ResultadoFavoritos   // 401: token vencido
    data object ErrorServidor : ResultadoFavoritos   // cualquier otro código HTTP
    data object SinConexion : ResultadoFavoritos     // no se pudo llegar al backend
}

// Resultado de guardar o quitar un favorito (H43, H45)
sealed interface ResultadoAccionFavorito {
    data object Exito : ResultadoAccionFavorito
    data object SesionVencida : ResultadoAccionFavorito   // 401: token vencido
    data object NoDisponible : ResultadoAccionFavorito    // 404: el producto ya no se ve
    data object ErrorServidor : ResultadoAccionFavorito   // cualquier otro código HTTP
    data object SinConexion : ResultadoAccionFavorito     // no se pudo llegar al backend
}

// Repository (MVVM): única capa que habla con la API de favoritos (H43, H44, H45)
class FavoritoRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)

    // GET /favoritos (H44)
    suspend fun obtener(): ResultadoFavoritos =
        try {
            val response = api.obtenerFavoritos(auth = tokenManager.getBearer())
            when {
                response.isSuccessful -> ResultadoFavoritos.Exito(response.body() ?: emptyList())
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoFavoritos.SesionVencida
                }
                else -> ResultadoFavoritos.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoFavoritos.SinConexion
        }

    // POST /favoritos (H43)
    suspend fun agregar(productoId: Long): ResultadoAccionFavorito =
        try {
            val response = api.agregarFavorito(
                auth = tokenManager.getBearer(),
                request = FavoritoRequest(productoId)
            )
            when {
                response.isSuccessful -> ResultadoAccionFavorito.Exito
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoAccionFavorito.SesionVencida
                }
                response.code() == 404 -> ResultadoAccionFavorito.NoDisponible
                else -> ResultadoAccionFavorito.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAccionFavorito.SinConexion
        }

    // DELETE /favoritos/{productoId} (H45)
    suspend fun quitar(productoId: Long): ResultadoAccionFavorito =
        try {
            val response = api.quitarFavorito(
                auth = tokenManager.getBearer(),
                productoId = productoId
            )
            when {
                response.isSuccessful -> ResultadoAccionFavorito.Exito
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoAccionFavorito.SesionVencida
                }
                else -> ResultadoAccionFavorito.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAccionFavorito.SinConexion
        }
}