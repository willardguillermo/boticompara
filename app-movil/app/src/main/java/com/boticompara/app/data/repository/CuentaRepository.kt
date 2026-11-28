package com.boticompara.app.data.repository

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.RetrofitClient
import com.boticompara.app.data.remote.dto.AuthResponse
import com.boticompara.app.data.remote.dto.CodigoCorreoRequest
import com.boticompara.app.data.remote.dto.CorreoRequest
import com.boticompara.app.data.remote.dto.LoginRequest
import com.boticompara.app.data.remote.dto.MensajeResponse
import com.boticompara.app.data.remote.dto.RecuperarRequest
import com.boticompara.app.data.remote.dto.RegistroRequest
import com.boticompara.app.data.remote.dto.RestablecerRequest
import com.boticompara.app.data.remote.dto.TokenVerificacionResponse
import kotlinx.coroutines.CancellationException
import retrofit2.Response

// Resultado de eliminar la cuenta (H19), sin depender de Retrofit en el ViewModel
sealed interface ResultadoCuenta {
    data object Exito : ResultadoCuenta
    data object DatosInvalidos : ResultadoCuenta   // 400
    data object SesionVencida : ResultadoCuenta    // 401: token vencido o inválido
    data object ErrorServidor : ResultadoCuenta    // cualquier otro código HTTP
    data object SinConexion : ResultadoCuenta      // no se pudo llegar al backend
}

/**
 * Acceso a la cuenta: login, registro con el correo verificado, verificación de cuentas antiguas y
 * recuperación de la contraseña (H17, H18, H20). Es una interfaz para probar los ViewModels sin Android.
 */
interface AccesoRepository {
    suspend fun login(correo: String, password: String): ResultadoApi<AuthResponse>
    suspend fun guardarSesion(sesion: AuthResponse)
    suspend fun cerrarSesion()

    // H20: registro del comprador
    suspend fun pedirCodigoRegistro(correo: String): ResultadoApi<MensajeResponse>
    suspend fun verificarCodigoRegistro(correo: String, codigo: String): ResultadoApi<TokenVerificacionResponse>
    suspend fun registrar(request: RegistroRequest): ResultadoApi<AuthResponse>

    // H20: cuentas creadas antes del registro con código (login 403 CORREO_NO_VERIFICADO)
    suspend fun pedirCodigoVerificacion(correo: String): ResultadoApi<MensajeResponse>
    suspend fun confirmarVerificacion(correo: String, codigo: String): ResultadoApi<MensajeResponse>

    // H17: recuperar la contraseña con un código
    suspend fun pedirCodigoRecuperacion(correo: String): ResultadoApi<MensajeResponse>
    suspend fun restablecer(correo: String, codigo: String, password: String): ResultadoApi<MensajeResponse>
}

// Repository (MVVM): única capa que habla con la API para las acciones de cuenta (H17, H18, H19, H20)
class CuentaRepository(context: Context) : AccesoRepository {
    private val api = RetrofitClient.getInstance(context)
    private val tokenManager = TokenManager(context)

    override suspend fun login(correo: String, password: String) =
        llamar { api.login(LoginRequest(correo, password)) }

    override suspend fun guardarSesion(sesion: AuthResponse) =
        tokenManager.saveSession(sesion.token, sesion.usuario.nombre, sesion.usuario.rol)

    override suspend fun cerrarSesion() = tokenManager.clearSession()

    override suspend fun pedirCodigoRegistro(correo: String) =
        llamar { api.registroCodigo(CorreoRequest(correo)) }

    override suspend fun verificarCodigoRegistro(correo: String, codigo: String) =
        llamar { api.registroVerificar(CodigoCorreoRequest(correo, codigo)) }

    override suspend fun registrar(request: RegistroRequest) = llamar { api.registro(request) }

    override suspend fun pedirCodigoVerificacion(correo: String) =
        llamar { api.verificacionCodigo(CorreoRequest(correo)) }

    override suspend fun confirmarVerificacion(correo: String, codigo: String) =
        llamar { api.verificacionConfirmar(CodigoCorreoRequest(correo, codigo)) }

    // H17: POST /auth/recuperar (siempre responde 200, exista o no el correo)
    override suspend fun pedirCodigoRecuperacion(correo: String) =
        llamar { api.recuperar(RecuperarRequest(correo)) }

    // H17: POST /auth/restablecer
    override suspend fun restablecer(correo: String, codigo: String, password: String) =
        llamar { api.restablecer(RestablecerRequest(correo, codigo, password)) }

    // H19: DELETE /usuarios/me. Si sale bien (o el token ya no sirve) se borra la sesión guardada.
    suspend fun eliminarCuenta(): ResultadoCuenta {
        val resultado = try {
            when (api.eliminarCuenta(tokenManager.getBearer()).code()) {
                in 200..299 -> ResultadoCuenta.Exito
                400 -> ResultadoCuenta.DatosInvalidos
                401 -> ResultadoCuenta.SesionVencida
                else -> ResultadoCuenta.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoCuenta.SinConexion
        }
        if (resultado == ResultadoCuenta.Exito || resultado == ResultadoCuenta.SesionVencida) {
            tokenManager.clearSession()
        }
        return resultado
    }

    // Convierte la respuesta de Retrofit (o la excepción de red) en un ResultadoApi
    private suspend fun <T> llamar(llamada: suspend () -> Response<T>): ResultadoApi<T> =
        try {
            val respuesta = llamada()
            ResultadoApi.desde(
                status = respuesta.code(),
                cuerpo = respuesta.body(),
                cuerpoError = if (respuesta.isSuccessful) null else respuesta.errorBody()?.string(),
                retryAfter = respuesta.headers()["Retry-After"]
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoApi.SinConexion
        }
}
