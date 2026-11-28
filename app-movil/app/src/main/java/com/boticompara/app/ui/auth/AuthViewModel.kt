package com.boticompara.app.ui.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boticompara.app.data.repository.AccesoRepository
import com.boticompara.app.data.repository.CuentaRepository
import com.boticompara.app.data.repository.GoogleAuthRepository
import com.boticompara.app.data.repository.ResultadoApi
import com.boticompara.app.data.repository.ResultadoGoogle
import com.boticompara.app.ui.auth.codigo.ESPERA_POR_DEFECTO
import com.boticompara.app.ui.auth.codigo.VerificacionCodigoEstado
import com.boticompara.app.ui.auth.codigo.segundosDeEspera
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AccesoRepository,
    private val iniciarConGoogle: suspend (Context) -> ResultadoGoogle
) : ViewModel() {

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    // H20: la cuenta es anterior al registro con código y el login respondió 403 CORREO_NO_VERIFICADO.
    // Mientras no sea null, la pantalla muestra la tarjeta del código en lugar del formulario.
    var verificacion by mutableStateOf<VerificacionCodigoEstado?>(null)
        private set
    var tituloVerificacion by mutableStateOf("Verifica tu correo para entrar.")
        private set
    var correoVerificacion by mutableStateOf("")
        private set

    // La contraseña que ya escribió, para repetir el login al verificar. Solo en memoria.
    private var passwordPendiente: String? = null

    // H18
    fun login(correo: String, contrasena: String, onSuccess: () -> Unit) {
        if (isLoading) return
        val limpio = correo.trim()
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val resultado = repository.login(limpio, contrasena)
            when {
                resultado is ResultadoApi.Exito -> {
                    repository.guardarSesion(resultado.valor)
                    isLoading = false
                    onSuccess()
                    return@launch
                }
                resultado is ResultadoApi.Fallo && resultado.status == 403 &&
                    resultado.codigo == CORREO_NO_VERIFICADO -> abrirVerificacion(limpio, contrasena, resultado)
                else -> errorMessage = mensajeLogin(resultado)
            }
            isLoading = false
        }
    }

    // H20: pide el código (POST /auth/verificacion/codigo) y abre la tarjeta. Un 429 se muestra con su espera.
    private suspend fun abrirVerificacion(correo: String, contrasena: String, fallo: ResultadoApi.Fallo) {
        correoVerificacion = correo
        passwordPendiente = contrasena
        tituloVerificacion = conPuntoFinal(fallo.mensaje ?: "Verifica tu correo para entrar")
        val estado = VerificacionCodigoEstado(viewModelScope)
        verificacion = estado
        when (val pedido = repository.pedirCodigoVerificacion(correo)) {
            is ResultadoApi.Exito -> estado.espera.iniciar(ESPERA_POR_DEFECTO)
            is ResultadoApi.Fallo -> {
                estado.mostrarError(mensajeDe(pedido, "No se pudo enviar el código. Usa «Reenviar»."))
                if (pedido.status == 429) estado.espera.iniciar(segundosDeEspera(pedido.retryAfter))
            }
            ResultadoApi.SinConexion -> estado.mostrarError(SIN_CONEXION)
        }
    }

    /** Cambió el código; con los 6 dígitos se confirma solo. */
    fun cambiarCodigo(nuevo: String, onSuccess: () -> Unit) {
        if (verificacion?.cambiar(nuevo) != null) confirmarCodigo(onSuccess)
    }

    // H20: POST /auth/verificacion/confirmar y, si salió bien, el mismo login con la contraseña que ya escribió
    fun confirmarCodigo(onSuccess: () -> Unit) {
        val estado = verificacion ?: return
        estado.verificar(
            enviar = { codigo -> repository.confirmarVerificacion(correoVerificacion, codigo) },
            alVerificar = { entrarTrasVerificar(onSuccess) }
        )
    }

    private suspend fun entrarTrasVerificar(onSuccess: () -> Unit) {
        val contrasena = passwordPendiente
        passwordPendiente = null
        val resultado = if (contrasena != null) repository.login(correoVerificacion, contrasena) else null
        if (resultado is ResultadoApi.Exito) {
            repository.guardarSesion(resultado.valor)
            onSuccess()
        } else {
            verificacion = null
            errorMessage = resultado?.let { mensajeLogin(it) } ?: "Inicia sesión de nuevo."
        }
    }

    fun reenviarCodigo() {
        verificacion?.reenviar(aviso = "Te enviamos un código nuevo.") {
            repository.pedirCodigoVerificacion(correoVerificacion)
        }
    }

    /** "Volver al inicio de sesión": olvida el código y la contraseña pendiente. */
    fun cancelarVerificacion() {
        verificacion = null
        passwordPendiente = null
        errorMessage = null
    }

    // H67: iniciar sesión con Google. Necesita el contexto de la Activity para mostrar el selector de cuentas
    fun loginConGoogle(activityContext: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            var exito = false
            when (val resultado = iniciarConGoogle(activityContext)) {
                is ResultadoGoogle.Exito -> {
                    repository.guardarSesion(resultado.sesion)
                    exito = true
                }
                ResultadoGoogle.Cancelado -> Unit   // cerró el selector: no es un error
                ResultadoGoogle.SinCuentaGoogle ->
                    errorMessage = "No hay una cuenta de Google en este celular. Agrega una en Ajustes e inténtalo de nuevo"
                is ResultadoGoogle.Rechazado -> errorMessage = resultado.mensaje
                ResultadoGoogle.ErrorGoogle -> errorMessage = "No se pudo iniciar sesión con Google. Inténtalo de nuevo"
                ResultadoGoogle.ErrorServidor -> errorMessage = "No se pudo iniciar sesión con Google"
                ResultadoGoogle.SinConexion -> errorMessage = "No se pudo conectar con el servidor"
            }
            isLoading = false
            if (exito) onSuccess()
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.cerrarSesion()
            onDone()
        }
    }

    private fun mensajeLogin(resultado: ResultadoApi<*>) = when {
        resultado is ResultadoApi.Fallo && resultado.status == 401 ->
            conPuntoFinal(resultado.mensaje ?: "Correo o contraseña incorrectos")
        else -> mensajeDe(resultado, "No se pudo iniciar sesión")
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val google = GoogleAuthRepository(app)
                AuthViewModel(CuentaRepository(app)) { contexto -> google.iniciarSesion(contexto) }
            }
        }
    }
}
