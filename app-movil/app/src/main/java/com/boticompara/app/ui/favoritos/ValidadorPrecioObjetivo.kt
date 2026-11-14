package com.boticompara.app.ui.favoritos

import java.math.BigDecimal

sealed interface ResultadoPrecioObjetivo {
    data class Valido(val valor: Double) : ResultadoPrecioObjetivo
    data class Invalido(val mensaje: String) : ResultadoPrecioObjetivo
}

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
