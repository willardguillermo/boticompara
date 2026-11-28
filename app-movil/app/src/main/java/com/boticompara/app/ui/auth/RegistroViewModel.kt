package com.boticompara.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boticompara.app.data.remote.dto.RegistroRequest
import com.boticompara.app.data.repository.AccesoRepository
import com.boticompara.app.data.repository.CuentaRepository
import com.boticompara.app.data.repository.ResultadoApi
import com.boticompara.app.ui.auth.codigo.CuentaRegresiva
import com.boticompara.app.ui.auth.codigo.ESPERA_POR_DEFECTO
import com.boticompara.app.ui.auth.codigo.VerificacionCodigoEstado
import com.boticompara.app.ui.auth.codigo.segundosDeEspera
import kotlinx.coroutines.launch

/** Los 3 pasos del registro del comprador (H1, H15, H20). */
enum class PasoRegistro { CORREO, CODIGO, CUENTA }

/**
 * H1, H15, H20 - Registro del comprador con el correo verificado (docs/API.md, sección 1):
 *   1 correo → POST /auth/registro/codigo
 *   2 código → POST /auth/registro/verificar (da el tokenVerificacion)
 *   3 cuenta → POST /auth/registro con el token; el 201 trae la sesión y se entra directo.
 * El tokenVerificacion y la contraseña viven solo en este ViewModel: nunca en DataStore ni en el log.
 */
class RegistroViewModel(private val repository: AccesoRepository) : ViewModel() {

    var paso by mutableStateOf(PasoRegistro.CORREO)
        private set

    // ---------- Paso 1: correo ----------
    var correo by mutableStateOf("")
        private set

    /** El correo al que se envió el código (en minúsculas y sin espacios). */
    var correoVerificado by mutableStateOf("")
        private set
    var errorCorreo by mutableStateOf<String?>(null)
        private set

    /** 409: el correo ya tiene cuenta; la pantalla ofrece "Inicia sesión". */
    var correoYaRegistrado by mutableStateOf(false)
        private set

    /** Error general del paso en el que está (429, 503, token vencido, sin conexión…). */
    var aviso by mutableStateOf<String?>(null)
        private set
    var enviando by mutableStateOf(false)
        private set

    /** Espera tras un 429 al pedir el código (paso 1). La del reenvío está en `codigo.espera`. */
    val esperaPedir = CuentaRegresiva(viewModelScope)

    // ---------- Paso 2: código ----------
    val codigo = VerificacionCodigoEstado(viewModelScope)

    // ---------- Paso 3: cuenta ----------
    var nombre by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var confirmar by mutableStateOf("")
        private set
    var telefono by mutableStateOf("")
        private set
    var errorNombre by mutableStateOf<String?>(null)
        private set
    var errorPassword by mutableStateOf<String?>(null)
        private set
    var errorConfirmar by mutableStateOf<String?>(null)
        private set
    var errorTelefono by mutableStateOf<String?>(null)
        private set

    /** La cuenta se creó y la sesión quedó guardada: la pantalla entra a la búsqueda. */
    var registrado by mutableStateOf(false)
        private set

    private var tokenVerificacion: String? = null
    val tieneToken: Boolean get() = tokenVerificacion != null

    /** Se está verificando el código (la tarjeta anima): no se puede salir del paso. */
    val verificandoCodigo: Boolean get() = paso == PasoRegistro.CODIGO && codigo.ocupado && !codigo.exito

    fun cambiarCorreo(valor: String) {
        correo = valor
        errorCorreo = null
        correoYaRegistrado = false
        // Otro correo puede tener su propio límite: que lo diga el servidor
        if (esperaPedir.activa) {
            esperaPedir.detener()
            aviso = null
        }
    }

    fun pedirCodigo() {
        val limpio = correo.trim().lowercase()
        if (enviando || esperaPedir.activa) return
        aviso = null
        if (!esCorreoValido(limpio)) {
            errorCorreo = "Ingresa un correo válido."
            return
        }
        errorCorreo = null
        correoYaRegistrado = false
        enviando = true
        viewModelScope.launch {
            val resultado = repository.pedirCodigoRegistro(limpio)
            enviando = false
            when (resultado) {
                is ResultadoApi.Exito -> {
                    correoVerificado = limpio
                    tokenVerificacion = null   // un código nuevo anula el token anterior (contrato)
                    codigo.reiniciar()
                    codigo.espera.iniciar(ESPERA_POR_DEFECTO)
                    paso = PasoRegistro.CODIGO
                }
                is ResultadoApi.Fallo -> when (resultado.status) {
                    409 -> marcarYaRegistrado(resultado)
                    400 -> errorCorreo = mensajeDe(resultado, "Ingresa un correo válido.")
                    429 -> {
                        aviso = mensajeDe(resultado, "Espera un momento antes de pedir otro código.")
                        esperaPedir.iniciar(segundosDeEspera(resultado.retryAfter))
                    }
                    503 -> aviso = mensajeDe(resultado, "No pudimos enviar el correo. Intenta en unos minutos.")
                    else -> aviso = mensajeDe(resultado, "No se pudo enviar el código.")
                }
                ResultadoApi.SinConexion -> aviso = SIN_CONEXION
            }
        }
    }

    fun cambiarCodigo(nuevo: String) {
        if (codigo.cambiar(nuevo) != null) verificarCodigo()
    }

    fun verificarCodigo() {
        codigo.verificar(
            enviar = { repository.verificarCodigoRegistro(correoVerificado, it) },
            alVerificar = { respuesta ->
                tokenVerificacion = respuesta.tokenVerificacion
                aviso = null
                paso = PasoRegistro.CUENTA
            }
        )
    }

    fun reenviarCodigo() {
        codigo.reenviar(aviso = "Te enviamos un código nuevo. El anterior ya no sirve.") {
            repository.pedirCodigoRegistro(correoVerificado)
        }
    }

    /** "Cambiar correo" (o el botón atrás): vuelve al paso 1 y olvida el token. */
    fun volverAlCorreo() {
        if (verificandoCodigo || enviando) return
        tokenVerificacion = null
        aviso = null
        codigo.reiniciar()
        paso = PasoRegistro.CORREO
    }

    fun cambiarNombre(valor: String) { nombre = valor; errorNombre = null }
    fun cambiarPassword(valor: String) { password = valor; errorPassword = null }
    fun cambiarConfirmar(valor: String) { confirmar = valor; errorConfirmar = null }
    fun cambiarTelefono(valor: String) {
        telefono = valor
        errorTelefono = if (esTelefonoValido(valor)) null else "Usa solo números (6 a 15 dígitos)."
    }

    private fun validarCuenta(): Boolean {
        val limpio = nombre.trim()
        errorNombre = when {
            limpio.isEmpty() -> "Ingresa tu nombre completo."
            limpio.length > 100 -> "El nombre admite máximo 100 caracteres."
            else -> null
        }
        errorPassword = when {
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
            password.length > 72 -> "La contraseña admite máximo 72 caracteres."
            else -> null
        }
        errorConfirmar = if (confirmar != password) "Las contraseñas no coinciden." else null
        errorTelefono = if (esTelefonoValido(telefono)) null else "Usa solo números (6 a 15 dígitos)."
        return listOf(errorNombre, errorPassword, errorConfirmar, errorTelefono).all { it == null }
    }

    fun crearCuenta() {
        if (enviando || registrado || !validarCuenta()) return
        val token = tokenVerificacion
        if (token == null) {
            volverAlCorreoConAviso("Tu verificación del correo venció. Vuelve a pedir un código.")
            return
        }
        aviso = null
        enviando = true
        viewModelScope.launch {
            val resultado = repository.registrar(
                RegistroRequest(
                    tokenVerificacion = token,
                    nombre = nombre.trim(),
                    correo = correoVerificado,
                    password = password,
                    telefono = telefono.trim().ifBlank { null },
                    rol = "COMPRADOR"
                )
            )
            when (resultado) {
                is ResultadoApi.Exito -> {
                    tokenVerificacion = null
                    repository.guardarSesion(resultado.valor)
                    password = ""
                    confirmar = ""
                    registrado = true
                }
                is ResultadoApi.Fallo -> when {
                    esTokenVencido(resultado) ->
                        volverAlCorreoConAviso(mensajeDe(resultado, "Tu verificación del correo venció"))
                    resultado.status == 409 -> {
                        tokenVerificacion = null
                        codigo.reiniciar()
                        paso = PasoRegistro.CORREO
                        marcarYaRegistrado(resultado)
                    }
                    resultado.status == 400 && mencionaContrasena(resultado) ->
                        errorPassword = mensajeDe(resultado, "Revisa la contraseña.")
                    resultado.status == 400 && resultado.mensaje.orEmpty().contains("nombre", ignoreCase = true) ->
                        errorNombre = mensajeDe(resultado, "Revisa tu nombre.")
                    else -> aviso = mensajeDe(resultado, "No se pudo crear la cuenta.")
                }
                ResultadoApi.SinConexion -> aviso = SIN_CONEXION
            }
            enviando = false
        }
    }

    /** El token venció o ya no sirve: vuelve al correo, que queda escrito, con un mensaje claro. */
    private fun volverAlCorreoConAviso(mensaje: String) {
        tokenVerificacion = null
        codigo.reiniciar()
        paso = PasoRegistro.CORREO
        aviso = mensaje
    }

    private fun marcarYaRegistrado(fallo: ResultadoApi.Fallo) {
        errorCorreo = mensajeDe(fallo, "El correo ya está registrado.")
        correoYaRegistrado = true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                RegistroViewModel(CuentaRepository(app))
            }
        }
    }
}
