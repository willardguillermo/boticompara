package com.boticompara.app.ui.auth

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
import com.boticompara.app.data.repository.ResultadoApi
import com.boticompara.app.ui.auth.codigo.ESPERA_POR_DEFECTO
import com.boticompara.app.ui.auth.codigo.VerificacionCodigoEstado
import com.boticompara.app.ui.auth.codigo.segundosDeEspera
import kotlinx.coroutines.launch

enum class PasoRecuperar { CORREO, CODIGO }

/**
 * H17 - Recuperar la contraseña con un código (docs/API.md, /auth/recuperar y /auth/restablecer):
 *   1 correo → POST /auth/recuperar (siempre 200, para no revelar qué correos existen)
 *   2 código + contraseña nueva → POST /auth/restablecer
 * Si el error es del código, la cruz falla y las casillas se limpian; si es de la contraseña,
 * el mensaje va en el campo y el código se queda. El código y la contraseña solo viven aquí.
 */
class RecuperarViewModel(private val repository: AccesoRepository) : ViewModel() {

    var paso by mutableStateOf(PasoRecuperar.CORREO)
        private set
    var correo by mutableStateOf("")
        private set
    private var correoEnviado = ""
    var errorCorreo by mutableStateOf<String?>(null)
        private set
    var aviso by mutableStateOf<String?>(null)
        private set

    /** Siempre el mensaje del servidor, para no revelar si el correo existe. */
    var mensajeServidor by mutableStateOf<String?>(null)
        private set
    var enviando by mutableStateOf(false)
        private set

    val codigo = VerificacionCodigoEstado(viewModelScope)

    var password by mutableStateOf("")
        private set
    var confirmar by mutableStateOf("")
        private set
    var errorPassword by mutableStateOf<String?>(null)
        private set
    var errorConfirmar by mutableStateOf<String?>(null)
        private set

    /** La contraseña cambió: la pantalla vuelve al login con el aviso. */
    var restablecido by mutableStateOf(false)
        private set

    val correoParaLogin: String get() = correoEnviado

    fun cambiarCorreo(valor: String) {
        correo = valor
        errorCorreo = null
    }

    fun cambiarPassword(valor: String) { password = valor; errorPassword = null }
    fun cambiarConfirmar(valor: String) { confirmar = valor; errorConfirmar = null }

    fun pedirCodigo() {
        if (enviando || codigo.espera.activa) return
        val limpio = correo.trim()
        aviso = null
        if (!esCorreoValido(limpio)) {
            errorCorreo = "Escribe un correo válido."
            return
        }
        errorCorreo = null
        enviando = true
        viewModelScope.launch {
            val resultado = repository.pedirCodigoRecuperacion(limpio)
            enviando = false
            when (resultado) {
                is ResultadoApi.Exito -> {
                    correoEnviado = limpio
                    mensajeServidor = conPuntoFinal(resultado.valor.mensaje)
                    codigo.reiniciar()
                    codigo.espera.iniciar(ESPERA_POR_DEFECTO)
                    paso = PasoRecuperar.CODIGO
                }
                is ResultadoApi.Fallo -> when (resultado.status) {
                    400 -> errorCorreo = mensajeDe(resultado, "Escribe un correo válido.")
                    429 -> {
                        aviso = mensajeDe(resultado, "Hiciste demasiadas solicitudes. Intenta más tarde.")
                        codigo.espera.iniciar(segundosDeEspera(resultado.retryAfter))
                    }
                    else -> aviso = mensajeDe(resultado, "No se pudo enviar el código.")
                }
                ResultadoApi.SinConexion -> aviso = SIN_CONEXION
            }
        }
    }

    /** El código se manda junto con la contraseña: no se envía solo al completar las casillas. */
    fun cambiarCodigo(nuevo: String) {
        codigo.cambiar(nuevo)
    }

    private fun validarPassword(): Boolean {
        errorPassword = when {
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
            password.length > 72 -> "La contraseña admite máximo 72 caracteres."
            else -> null
        }
        errorConfirmar = if (confirmar != password) "Las contraseñas no coinciden." else null
        return errorPassword == null && errorConfirmar == null
    }

    fun cambiarContrasena() {
        if (codigo.ocupado || restablecido) return
        val passwordValida = validarPassword()
        if (!passwordValida) {
            codigo.exigirCompleto()   // también avisa si falta el código
            return
        }
        codigo.verificar(
            enviar = { repository.restablecer(correoEnviado, it, password) },
            esErrorDeCodigo = { esErrorDelCodigo(it) },
            alOtroError = { resultado ->
                if (resultado is ResultadoApi.Fallo && resultado.status == 400 && mencionaContrasena(resultado)) {
                    errorPassword = mensajeDe(resultado, "Revisa la contraseña.")
                } else {
                    codigo.mostrarError(mensajeDe(resultado, "No se pudo cambiar la contraseña."))
                }
            },
            anuncioExito = "Contraseña actualizada",
            alVerificar = {
                password = ""
                confirmar = ""
                restablecido = true
            }
        )
    }

    fun reenviarCodigo() {
        codigo.reenviar(aviso = "Si el correo está registrado, te enviamos un código nuevo.") {
            repository.pedirCodigoRecuperacion(correoEnviado)
        }
    }

    /** "Usar otro correo": vuelve al paso 1 sin la contraseña escrita. */
    fun usarOtroCorreo() {
        if (codigo.ocupado) return
        password = ""
        confirmar = ""
        errorPassword = null
        errorConfirmar = null
        aviso = null
        mensajeServidor = null
        codigo.reiniciar()
        paso = PasoRecuperar.CORREO
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                RecuperarViewModel(CuentaRepository(app))
            }
        }
    }
}
