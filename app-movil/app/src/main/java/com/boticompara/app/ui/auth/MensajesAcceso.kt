package com.boticompara.app.ui.auth

import com.boticompara.app.data.repository.ResultadoApi

// Textos y reglas compartidas por el login, el registro y la recuperación de la contraseña (H17, H18, H20)

const val SIN_CONEXION = "No se pudo conectar con el servidor."

/** El único error que trae `codigo` (docs/API.md, POST /auth/login). */
const val CORREO_NO_VERIFICADO = "CORREO_NO_VERIFICADO"

private val CORREO_VALIDO = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val TELEFONO_VALIDO = Regex("^[0-9]{6,15}$")
private val TOKEN_VENCIDO = Regex("verificaci[oó]n del correo venci", RegexOption.IGNORE_CASE)
private val MENCIONA_CODIGO = Regex("c[oó]digo", RegexOption.IGNORE_CASE)
private val MENCIONA_CONTRASENA = Regex("contrase[ñn]a|password", RegexOption.IGNORE_CASE)

fun esCorreoValido(correo: String) = CORREO_VALIDO.matches(correo.trim())

/** El teléfono es opcional: vacío vale; si no, de 6 a 15 dígitos. */
fun esTelefonoValido(telefono: String) = telefono.isBlank() || TELEFONO_VALIDO.matches(telefono.trim())

/** Los mensajes del servidor no traen punto final ("El correo ya está registrado"): la app se lo pone. */
fun conPuntoFinal(texto: String): String {
    val limpio = texto.trim()
    return if (limpio.isEmpty() || limpio.last() in ".!?…") limpio else "$limpio."
}

/** El mensaje que se muestra para un resultado que no salió bien. */
fun mensajeDe(resultado: ResultadoApi<*>, porDefecto: String): String = when (resultado) {
    is ResultadoApi.Fallo -> conPuntoFinal(resultado.mensaje ?: porDefecto)
    ResultadoApi.SinConexion -> SIN_CONEXION
    is ResultadoApi.Exito -> conPuntoFinal(porDefecto)
}

/** El tokenVerificacion venció, ya se usó o es de otro correo (400 de POST /auth/registro). */
fun esTokenVencido(fallo: ResultadoApi.Fallo) =
    fallo.status == 400 && TOKEN_VENCIDO.containsMatchIn(fallo.mensaje.orEmpty())

/** 400 de POST /auth/restablecer por el código (no por la contraseña nueva). */
fun esErrorDelCodigo(fallo: ResultadoApi.Fallo) =
    fallo.status == 400 && MENCIONA_CODIGO.containsMatchIn(fallo.mensaje.orEmpty())

fun mencionaContrasena(fallo: ResultadoApi.Fallo) = MENCIONA_CONTRASENA.containsMatchIn(fallo.mensaje.orEmpty())
