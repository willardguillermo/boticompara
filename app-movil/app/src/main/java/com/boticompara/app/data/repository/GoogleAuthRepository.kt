package com.boticompara.app.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.boticompara.app.R
import com.boticompara.app.data.remote.RetrofitClient
import com.boticompara.app.data.remote.dto.AuthResponse
import com.boticompara.app.data.remote.dto.GoogleAuthRequest
import com.boticompara.app.data.remote.dto.MensajeResponse
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Response
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Resultado de iniciar sesión con Google, sin depender de Firebase ni de Retrofit en el ViewModel (H67)
sealed interface ResultadoGoogle {
    data class Exito(val sesion: AuthResponse) : ResultadoGoogle
    data object Cancelado : ResultadoGoogle          // cerró el selector de cuentas
    data object SinCuentaGoogle : ResultadoGoogle    // el celular no tiene ninguna cuenta de Google
    data class Rechazado(val mensaje: String) : ResultadoGoogle   // 401, 403 o 409 del servidor, con su mensaje
    data object ErrorGoogle : ResultadoGoogle        // falló Google o Firebase
    data object ErrorServidor : ResultadoGoogle      // cualquier otro código HTTP
    data object SinConexion : ResultadoGoogle        // no se pudo llegar al backend
}

// Repository (MVVM): único lugar que habla con Google, Firebase y POST /auth/google (H67)
// Flujo: cuenta de Google -> token de Google -> sesión de Firebase -> token de Firebase -> nuestro JWT
class GoogleAuthRepository(private val context: Context) {
    private val api = RetrofitClient.getInstance(context)

    // activityContext: el selector de cuentas de Google necesita una Activity para mostrarse
    suspend fun iniciarSesion(activityContext: Context): ResultadoGoogle {
        val tokenFirebase = obtenerTokenFirebase(activityContext)
        return when (tokenFirebase) {
            is TokenFirebase.Listo -> enviarAlServidor(tokenFirebase.idToken)
            TokenFirebase.Cancelado -> ResultadoGoogle.Cancelado
            TokenFirebase.SinCuenta -> ResultadoGoogle.SinCuentaGoogle
            TokenFirebase.Fallo -> ResultadoGoogle.ErrorGoogle
        }
    }

    private sealed interface TokenFirebase {
        data class Listo(val idToken: String) : TokenFirebase
        data object Cancelado : TokenFirebase
        data object SinCuenta : TokenFirebase
        data object Fallo : TokenFirebase
    }

    private suspend fun obtenerTokenFirebase(activityContext: Context): TokenFirebase =
        try {
            // 1. El usuario elige su cuenta de Google. default_web_client_id lo genera el plugin
            //    de Google Services a partir de google-services.json
            val webClientId = context.getString(R.string.default_web_client_id)
            val pedido = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
            val credencial = CredentialManager.create(activityContext)
                .getCredential(activityContext, pedido)
                .credential

            if (credencial !is CustomCredential ||
                credencial.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                TokenFirebase.Fallo
            } else {
                // 2. Con el token de Google se inicia sesión en Firebase
                val tokenGoogle = GoogleIdTokenCredential.createFrom(credencial.data).idToken
                val auth = FirebaseAuth.getInstance()
                val usuario = auth.signInWithCredential(GoogleAuthProvider.getCredential(tokenGoogle, null))
                    .esperar().user

                // 3. El servidor verifica el token de Firebase (no el de Google)
                val idToken = usuario?.getIdToken(true)?.esperar()?.token

                // La sesión de Firebase no se usa: la app sigue con nuestro JWT
                auth.signOut()
                if (idToken.isNullOrEmpty()) TokenFirebase.Fallo else TokenFirebase.Listo(idToken)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            TokenFirebase.Cancelado
        } catch (e: NoCredentialException) {
            TokenFirebase.SinCuenta
        } catch (e: GetCredentialException) {
            TokenFirebase.Fallo
        } catch (e: GoogleIdTokenParsingException) {
            TokenFirebase.Fallo
        } catch (e: Exception) {
            TokenFirebase.Fallo
        }

    // POST /auth/google (H67)
    private suspend fun enviarAlServidor(idToken: String): ResultadoGoogle =
        try {
            val response = api.loginGoogle(GoogleAuthRequest(idToken))
            val cuerpo = response.body()
            when {
                response.isSuccessful && cuerpo != null -> ResultadoGoogle.Exito(cuerpo)
                response.code() == 401 ->
                    ResultadoGoogle.Rechazado(mensajeDe(response) ?: "No pudimos verificar tu cuenta de Google")
                response.code() == 403 ->
                    ResultadoGoogle.Rechazado(mensajeDe(response) ?: "Tu cuenta no puede iniciar sesión")
                response.code() == 409 ->
                    ResultadoGoogle.Rechazado(
                        mensajeDe(response) ?: "Ya existe una cuenta con este correo. Inicia sesión con tu contraseña"
                    )
                else -> ResultadoGoogle.ErrorServidor
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoGoogle.SinConexion
        }

    // El servidor responde los errores con { "status", "error", "mensaje", "timestamp" }
    private fun mensajeDe(response: Response<*>): String? =
        try {
            Gson().fromJson(response.errorBody()?.string(), MensajeResponse::class.java)?.mensaje
        } catch (e: Exception) {
            null
        }
}

// Convierte la Task de Google Play Services en una función suspend (así no hace falta otra librería)
private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuacion ->
    addOnSuccessListener { continuacion.resume(it) }
    addOnFailureListener { continuacion.resumeWithException(it) }
    addOnCanceledListener { continuacion.cancel() }
}
