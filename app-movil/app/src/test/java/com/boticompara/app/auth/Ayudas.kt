package com.boticompara.app.auth

import com.boticompara.app.data.remote.dto.AuthResponse
import com.boticompara.app.data.remote.dto.MensajeResponse
import com.boticompara.app.data.remote.dto.UsuarioResponse
import com.boticompara.app.data.repository.ResultadoApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

// Datos y ayudas compartidas por las pruebas del acceso (H17, H18, H20)

val SESION = AuthResponse(
    token = "eyJ.jwt.prueba",
    tipo = "Bearer",
    usuario = UsuarioResponse(4, "Carlos Ramírez", "carlos@boticompara.nissidev.dev", "COMPRADOR")
)
const val TOKEN = "9f2c4e0000000000000000000000000000000000000000000000000000000000"
val OK = ResultadoApi.Exito(MensajeResponse("ok"))

fun fallo(status: Int, mensaje: String? = null, codigo: String? = null, retryAfter: String? = null) =
    ResultadoApi.Fallo(status, mensaje, codigo, retryAfter)

/** Avanza el reloj virtual y corre lo que quedó listo justo en ese instante. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun TestScope.avanzar(ms: Long) {
    advanceTimeBy(ms)
    runCurrent()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun nuevoDespachador() = StandardTestDispatcher()

