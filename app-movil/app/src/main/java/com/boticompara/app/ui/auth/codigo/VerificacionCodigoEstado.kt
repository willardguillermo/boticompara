package com.boticompara.app.ui.auth.codigo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boticompara.app.data.repository.ResultadoApi
import com.boticompara.app.ui.auth.SIN_CONEXION
import com.boticompara.app.ui.auth.mensajeDe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Momentos de la verificación, que la tarjeta dibuja con la cruz LED de botica (CampoCodigo):
 * - ESCRIBIENDO: las 6 casillas.
 * - REUNIR: las casillas se juntan al centro y forman la cruz; una onda de luz va del centro a las puntas
 *   mientras se espera la respuesta (como mínimo 900 ms).
 * - FIJA: correcto, la cruz queda encendida.  - EXITO: se vuelve check y la tarjeta se tiñe de verde.
 * - FALLA: código incorrecto, parpadea 2 veces en rojo.  - VOLVER: vuelve a las 6 casillas con un rebote.
 */
enum class FaseCodigo { ESCRIBIENDO, REUNIR, FIJA, EXITO, FALLA, VOLVER }

/** Cuánto dura cada momento (ms). Con las animaciones del sistema apagadas son más cortos: solo hay fundidos. */
data class TiemposCodigo(val minimo: Long, val fija: Long, val exito: Long, val falla: Long, val volver: Long) {
    companion object {
        val NORMAL = TiemposCodigo(minimo = 900, fija = 300, exito = 1_100, falla = 650, volver = 600)
        val REDUCIDO = TiemposCodigo(minimo = 400, fija = 0, exito = 700, falla = 400, volver = 250)
    }
}

/**
 * H17, H20: estado de la tarjeta del código, compartido por el registro, el login (403 CORREO_NO_VERIFICADO)
 * y la recuperación de la contraseña. Lo crea el ViewModel con su viewModelScope: el código solo vive aquí,
 * en memoria. Las fases avanzan con delay(), así la tarjeta solo tiene que dibujarlas.
 */
class VerificacionCodigoEstado(private val scope: CoroutineScope) {
    var codigo by mutableStateOf("")
        private set
    var fase by mutableStateOf(FaseCodigo.ESCRIBIENDO)
        private set

    /** Error del código: va bajo las casillas y se anuncia. */
    var errorCodigo by mutableStateOf<String?>(null)
        private set

    /** Otros errores (sin conexión, 429, 503…): van arriba y no borran el código. */
    var errorGeneral by mutableStateOf<String?>(null)
        private set

    /** Aviso informativo, por ejemplo "Te enviamos un código nuevo." */
    var mensaje by mutableStateOf<String?>(null)
        private set

    /** Lo que anuncia TalkBack: "Verificando código" y "Correo verificado". */
    var anuncio by mutableStateOf("")
        private set

    /** Sube con cada código incorrecto: la fila se sacude y el foco vuelve a la primera casilla. */
    var sacudidas by mutableIntStateOf(0)
        private set

    var reenviando by mutableStateOf(false)
        private set

    /** Lo pone la tarjeta según la escala de animación del sistema. */
    var movimientoReducido = false

    /** "Reenviar en 00:38". */
    val espera = CuentaRegresiva(scope)

    val ocupado: Boolean get() = fase != FaseCodigo.ESCRIBIENDO
    val exito: Boolean get() = fase == FaseCodigo.EXITO

    /**
     * El usuario cambió el campo. Devuelve el código si con este cambio quedó completo (para enviarlo solo),
     * o null. Mientras se verifica no se cambia nada (por ejemplo, un pegado que llegue tarde).
     */
    fun cambiar(nuevo: String): String? {
        if (ocupado) return null
        val antes = codigo
        val limpio = ReglasCodigo.aplicarEntrada(antes, nuevo)
        codigo = limpio
        if (limpio != antes) errorCodigo = null
        val completo = ReglasCodigo.codigoCompleto(limpio)
        return completo?.takeIf { it != ReglasCodigo.codigoCompleto(antes) }
    }

    /** Si faltan dígitos, lo dice bajo las casillas y las sacude. */
    fun exigirCompleto(): Boolean {
        if (ReglasCodigo.codigoCompleto(codigo) != null) return true
        errorCodigo = "Escribe los 6 dígitos del código."
        sacudidas++
        return false
    }

    /**
     * Manda el código y anima la respuesta.
     * - enviar: la llamada a la API.
     * - esErrorDeCodigo: si el fallo es del código (la cruz falla en rojo y las casillas se limpian).
     * - alOtroError: cualquier otro error; por defecto va arriba sin borrar el código.
     * - alVerificar: se llama al terminar la animación del éxito, con lo que devolvió la API.
     */
    fun <T> verificar(
        enviar: suspend (String) -> ResultadoApi<T>,
        esErrorDeCodigo: (ResultadoApi.Fallo) -> Boolean = { it.status == 400 },
        alOtroError: (ResultadoApi<T>) -> Unit = { mostrarError(mensajeDe(it, "No se pudo verificar el código.")) },
        anuncioExito: String = "Correo verificado",
        alVerificar: suspend (T) -> Unit
    ) {
        if (ocupado || !exigirCompleto()) return
        val completo = codigo
        val t = if (movimientoReducido) TiemposCodigo.REDUCIDO else TiemposCodigo.NORMAL
        errorCodigo = null
        errorGeneral = null
        mensaje = null
        fase = FaseCodigo.REUNIR
        anuncio = "Verificando código"

        scope.launch {
            // La cruz se ve encendida al menos `minimo` aunque la API responda antes
            val resultado = coroutineScope {
                val pausa = launch { delay(t.minimo) }
                enviar(completo).also { pausa.join() }
            }

            if (resultado is ResultadoApi.Exito) {
                fase = FaseCodigo.FIJA
                delay(t.fija)
                fase = FaseCodigo.EXITO
                anuncio = anuncioExito
                delay(t.exito)
                alVerificar(resultado.valor)
                return@launch
            }

            val delCodigo = resultado is ResultadoApi.Fallo && esErrorDeCodigo(resultado)
            if (delCodigo) {
                fase = FaseCodigo.FALLA
                delay(t.falla)
            }
            fase = FaseCodigo.VOLVER
            delay(t.volver)
            fase = FaseCodigo.ESCRIBIENDO
            anuncio = ""
            if (delCodigo) {
                errorCodigo = mensajeDe(resultado, "El código no es válido.")
                codigo = ""
                sacudidas++
            } else {
                alOtroError(resultado)
            }
        }
    }

    /** Pide otro código. `aviso` es lo que se muestra si salió bien. */
    fun reenviar(aviso: String, pedir: suspend () -> ResultadoApi<*>) {
        if (ocupado || reenviando || espera.activa) return
        errorCodigo = null
        errorGeneral = null
        mensaje = null
        reenviando = true
        scope.launch {
            val resultado = pedir()
            reenviando = false
            when (resultado) {
                is ResultadoApi.Exito -> {
                    codigo = ""
                    mensaje = aviso
                    espera.iniciar(ESPERA_POR_DEFECTO)
                }
                is ResultadoApi.Fallo -> {
                    errorGeneral = mensajeDe(resultado, "No se pudo reenviar el código.")
                    if (resultado.status == 429) espera.iniciar(segundosDeEspera(resultado.retryAfter))
                }
                ResultadoApi.SinConexion -> errorGeneral = SIN_CONEXION
            }
        }
    }

    fun mostrarError(texto: String) {
        errorGeneral = texto
    }

    /** Vuelve a las casillas vacías (otro correo o un código nuevo). La espera del reenvío sigue. */
    fun reiniciar() {
        codigo = ""
        fase = FaseCodigo.ESCRIBIENDO
        errorCodigo = null
        errorGeneral = null
        mensaje = null
        anuncio = ""
    }
}
