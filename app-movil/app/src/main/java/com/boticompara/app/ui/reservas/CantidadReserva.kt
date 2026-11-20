package com.boticompara.app.ui.reservas

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// H74: la cantidad de una reserva va de 1 a 10 y no pasa del stock que se ve en el detalle.
// El servidor revisa además las unidades ya reservadas por otros (409 con su mensaje).
object CantidadReserva {
    const val MINIMA = 1
    const val MAXIMA = 10

    fun maximaPara(stock: Int): Int = stock.coerceIn(MINIMA, MAXIMA)

    fun limitar(cantidad: Int, stock: Int): Int = cantidad.coerceIn(MINIMA, maximaPara(stock))

    fun puedeAumentar(cantidad: Int, stock: Int): Boolean = cantidad < maximaPara(stock)

    fun puedeDisminuir(cantidad: Int): Boolean = cantidad > MINIMA

    fun aumentar(cantidad: Int, stock: Int): Int = limitar(cantidad + 1, stock)

    fun disminuir(cantidad: Int, stock: Int): Int = limitar(cantidad - 1, stock)

    // total = cantidad × precio unitario, con 2 decimales (igual que el servidor)
    fun total(precioUnitario: Double, cantidad: Int): BigDecimal =
        BigDecimal.valueOf(precioUnitario)
            .multiply(BigDecimal(cantidad))
            .setScale(2, RoundingMode.HALF_UP)
}

fun formatearSoles(monto: Double): String = "S/ ${String.format(Locale.US, "%.2f", monto)}"

fun formatearSoles(monto: BigDecimal): String = "S/ ${monto.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
