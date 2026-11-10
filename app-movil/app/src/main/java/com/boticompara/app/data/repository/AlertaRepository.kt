package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.AlertaResponse
import com.boticompara.app.data.remote.RetrofitClient
import kotlin.coroutines.cancellation.CancellationException

// Resultado de consultar la lista de alertas (H59)
sealed interface ResultadoAlertas {
    data class Exito(val alertas: List<AlertaResponse>) : ResultadoAlertas
    data object SesionVencida : ResultadoAlertas   // 401: token vencido
    data object ErrorServidor : ResultadoAlertas   // cualquier otro código HTTP
    data object SinConexion : ResultadoAlertas     // no se pudo llegar al backend
}

// Resultado de marcar una alerta (o todas) como leída (H59)
sealed interface ResultadoAccionAlerta {
    data object Exito : ResultadoAccionAlerta
    data object SesionVencida : ResultadoAccionAlerta   // 401: token vencido
    data object NoDisponible : ResultadoAccionAlerta    // 404: la alerta no existe o es de otro usuario
    data object ErrorServidor : ResultadoAccionAlerta   // cualquier otro código HTTP
    data object SinConexion : ResultadoAccionAlerta     // no se pudo llegar al backend
}

// Repository (MVVM): única capa que habla con la API de alertas de precio (H59)
class AlertaRepository(context: Context) {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)

    // GET /alertas?soloNoLeidas=true (H59)
    suspend fun obtenerNoLeidas(): ResultadoAlertas =
        try {
            val response = api.obtenerAlertas(
                auth = tokenManager.getBearer(),
                soloNoLeidas = true
            )
            when {
                response.isSuccessful -> ResultadoAlertas.Exito(response.body() ?: emptyList())
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoAlertas.SesionVencida
                }
                else -> ResultadoAlertas.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAlertas.SinConexion
        }

    // GET /alertas/resumen (H59). Devuelve null si falla: el número del ícono es secundario
    suspend fun obtenerResumen(): Int? =
        try {
            val response = api.obtenerResumenAlertas(auth = tokenManager.getBearer())
            if (response.isSuccessful) response.body()?.noLeidas else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    // PUT /alertas/{id}/leida (H59)
    suspend fun marcarLeida(id: Long): ResultadoAccionAlerta =
        try {
            val response = api.marcarAlertaLeida(
                auth = tokenManager.getBearer(),
                id = id
            )
            when {
                response.isSuccessful -> ResultadoAccionAlerta.Exito
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoAccionAlerta.SesionVencida
                }
                response.code() == 404 -> ResultadoAccionAlerta.NoDisponible
                else -> ResultadoAccionAlerta.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAccionAlerta.SinConexion
        }

    // PUT /alertas/leidas (H59)
    suspend fun marcarTodasLeidas(): ResultadoAccionAlerta =
        try {
            val response = api.marcarTodasLeidas(auth = tokenManager.getBearer())
            when {
                response.isSuccessful -> ResultadoAccionAlerta.Exito
                response.code() == 401 -> {
                    tokenManager.clearSession()
                    ResultadoAccionAlerta.SesionVencida
                }
                else -> ResultadoAccionAlerta.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAccionAlerta.SinConexion
        }
}