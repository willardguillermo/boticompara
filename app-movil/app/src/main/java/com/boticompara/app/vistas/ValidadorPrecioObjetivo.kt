package com.boticompara.app.vistas

import java.math.BigDecimal

// Resultado de validar el texto escrito como precio objetivo (H60)
sealed interface ResultadoPrecioObjetivo {
    data class Valido(val valor: Double) : ResultadoPrecioObjetivo
    data class Invalido(val mensaje: String) : ResultadoPrecioObjetivo
}

// Reglas del backend: mayor que 0, máximo 2 decimales y menor que 100000
object ValidadorPrecioObjetivo {
    private val FORMATO = Regex("^\\d+(\\.\\d+)?$")
    private val MAXIMO = BigDecimal("100000")

    fun validar(texto: String): ResultadoPrecioObjetivo {
        val limpio = texto.trim().replace(',', '.')
        if (limpio.isEmpty()) {
            return ResultadoPrecioObjetivo.Invalido("Ingresa un precio")
        }
        if (!FORMATO.matches(limpio)) {
            return ResultadoPrecioObjetivo.Invalido("Ingresa un número válido")
        }
        val numero = BigDecimal(limpio)
        return when {
            numero.signum() <= 0 ->
                ResultadoPrecioObjetivo.Invalido("El precio debe ser mayor que 0")
            numero.stripTrailingZeros().scale() > 2 ->
                ResultadoPrecioObjetivo.Invalido("Usa máximo 2 decimales")
            numero >= MAXIMO ->
                ResultadoPrecioObjetivo.Invalido("El precio debe ser menor que S/ 100000")
            else -> ResultadoPrecioObjetivo.Valido(numero.toDouble())
        }
    }
}