package com.boticompara.app.ui.auth.codigo

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * H17, H20: lógica de las 6 casillas del código, sin Compose para probarla con JUnit.
 * El valor es el texto del único campo que hay detrás de las casillas: solo dígitos, de 0 a 6.
 * Así el teclado, el pegado y el autocompletado funcionan como en un campo normal, y Backspace
 * borra el último dígito y la casilla activa retrocede.
 */
object ReglasCodigo {
    const val LARGO = 6

    private fun Char.esDigito() = this in '0'..'9'
    private fun String.soloDigitos() = filter { it.esDigito() }

    /**
     * Lo que queda en el campo después de que el usuario lo cambió (tecla, Backspace, pegado o autocompletado).
     * - Se quitan los espacios, guiones y letras ("Tu código es 483 920" → "483920").
     * - Si lo que se pegó trae 6 dígitos o más, reemplaza lo que había (pegar el código completo lo reparte).
     * - Si no, nunca pasa de 6 dígitos: lo que sobra se ignora.
     */
    fun aplicarEntrada(anterior: String, nuevo: String): String {
        val digitos = nuevo.soloDigitos()
        if (digitos.length <= LARGO) return digitos

        // Qué parte del texto es nueva: lo que no coincide con el principio ni con el final del anterior
        var inicio = 0
        while (inicio < anterior.length && inicio < nuevo.length && anterior[inicio] == nuevo[inicio]) inicio++
        var fin = 0
        while (fin < anterior.length - inicio && fin < nuevo.length - inicio &&
            anterior[anterior.length - 1 - fin] == nuevo[nuevo.length - 1 - fin]
        ) fin++
        val insertado = nuevo.substring(inicio, nuevo.length - fin).soloDigitos()
        return if (insertado.length >= LARGO) insertado.take(LARGO) else digitos.take(LARGO)
    }

    /** Las 6 casillas: el dígito de cada una o null si está vacía. */
    fun casillas(valor: String): List<Char?> = List(LARGO) { valor.getOrNull(it)?.takeIf { c -> c.esDigito() } }

    /** La casilla donde va el siguiente dígito (la última si ya están todas). */
    fun casillaActiva(valor: String): Int = min(valor.length, LARGO - 1)

    /** El código para enviar a la API: los 6 dígitos, o null si falta alguno. */
    fun codigoCompleto(valor: String): String? = valor.takeIf { it.length == LARGO && it.all { c -> c.esDigito() } }
}

/** Un punto (LED) de la cruz: su columna, su fila y `anillo`, la distancia al centro para la onda de luz. */
data class LedCruz(val columna: Int, val fila: Int, val anillo: Int)

/**
 * La cruz de botica hecha de LEDs, con la misma forma que el logo: un "+" de `lado` x `lado`
 * cuyos brazos miden `brazo` puntos de ancho. Con 9 y 3 son 45 puntos.
 */
fun puntosCruz(lado: Int = 9, brazo: Int = 3): List<LedCruz> {
    val centro = (lado - 1) / 2
    val desde = (lado - brazo) / 2
    fun enBrazo(v: Int) = v >= desde && v < desde + brazo
    val puntos = mutableListOf<LedCruz>()
    for (fila in 0 until lado) {
        for (columna in 0 until lado) {
            if (!enBrazo(fila) && !enBrazo(columna)) continue
            puntos += LedCruz(columna, fila, max(abs(fila - centro), abs(columna - centro)))
        }
    }
    return puntos
}
