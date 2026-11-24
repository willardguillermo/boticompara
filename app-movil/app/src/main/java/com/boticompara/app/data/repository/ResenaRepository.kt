package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.RetrofitClient
import com.boticompara.app.data.remote.dto.ErrorApiResponse
import com.boticompara.app.data.remote.dto.ResenaRequest
import com.boticompara.app.data.remote.dto.ResenaResponse
import com.boticompara.app.data.remote.dto.ResenasResponse
import com.google.gson.Gson
import retrofit2.Response
import kotlin.coroutines.cancellation.CancellationException

sealed interface ResultadoResena {
    data class Exito(val resena: ResenaResponse) : ResultadoResena
    data object SesionVencida : ResultadoResena
    data class Rechazada(val mensaje: String, val codigoHttp: Int) : ResultadoResena
    data object ErrorServidor : ResultadoResena
    data object SinConexion : ResultadoResena
}

sealed interface ResultadoResenas {
    data class Exito(val resenas: ResenasResponse) : ResultadoResenas
    data object SesionVencida : ResultadoResenas
    data class Rechazada(val mensaje: String, val codigoHttp: Int) : ResultadoResenas
    data object ErrorServidor : ResultadoResenas
    data object SinConexion : ResultadoResenas
}

object ErroresResena {
    val codigosConMensaje = setOf(400, 403, 404, 429)

    fun mensaje(codigo: Int, mensajeServidor: String?, mensajePorDefecto: String): String? =
        if (codigo in codigosConMensaje) {
            mensajeServidor?.takeIf { it.isNotBlank() } ?: mensajePorDefecto
        } else {
            null
        }
}

class ResenaRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)
    private val gson = Gson()

    suspend fun listar(boticaId: Long, page: Int): ResultadoResenas =
        try {
            val response = api.obtenerResenas(boticaId, page)
            val cuerpo = response.body()
            when {
                response.isSuccessful && cuerpo != null -> ResultadoResenas.Exito(cuerpo)
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoResenas.SesionVencida
                }
                response.code() in ErroresResena.codigosConMensaje ->
                    ResultadoResenas.Rechazada(
                        ErroresResena.mensaje(
                            response.code(),
                            mensajeDelServidor(response),
                            "No se pudieron cargar las reseñas"
                        )!!,
                        response.code()
                    )
                else -> ResultadoResenas.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoResenas.SinConexion
        }

    suspend fun guardar(boticaId: Long, estrellas: Int, comentario: String?): ResultadoResena =
        try {
            val response = api.guardarResena(
                tokenManager.getBearer(),
                boticaId,
                ResenaRequest(estrellas, comentario?.trim()?.takeIf { it.isNotEmpty() })
            )
            val cuerpo = response.body()
            when {
                response.isSuccessful && cuerpo != null -> ResultadoResena.Exito(cuerpo)
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoResena.SesionVencida
                }
                response.code() in ErroresResena.codigosConMensaje ->
                    ResultadoResena.Rechazada(
                        ErroresResena.mensaje(
                            response.code(),
                            mensajeDelServidor(response),
                            "No se pudo guardar la reseña"
                        )!!,
                        response.code()
                    )
                else -> ResultadoResena.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoResena.SinConexion
        }

    private fun mensajeDelServidor(response: Response<*>): String? =
        try {
            response.errorBody()?.string()
                ?.let { gson.fromJson(it, ErrorApiResponse::class.java) }
                ?.mensaje
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
}
