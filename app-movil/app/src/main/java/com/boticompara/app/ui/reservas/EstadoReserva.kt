package com.boticompara.app.ui.reservas

import com.boticompara.app.ui.components.ReglasCondicionVenta

// Tono de color de cada estado, el mismo que usa la web del dueño para el pedido.
// El color concreto está en ColoresReserva (EstadoReservaChip.kt)
enum class TonoEstado { ALERTA, INFORMACION, PRIMARIO, EXITO, ERROR, NEUTRO }

// H76: los 7 estados de una reserva (docs/API.md, sección 9) con un nombre claro en español
enum class EstadoReserva(val nombre: String, val tono: TonoEstado, val cancelable: Boolean) {
    PENDIENTE("Pendiente", TonoEstado.ALERTA, cancelable = true),
    CONFIRMADA("Confirmada", TonoEstado.INFORMACION, cancelable = true),
    LISTA("Lista para recoger", TonoEstado.PRIMARIO, cancelable = true),
    ENTREGADA("Entregada", TonoEstado.EXITO, cancelable = false),
    RECHAZADA("Rechazada", TonoEstado.ERROR, cancelable = false),
    CANCELADA("Cancelada", TonoEstado.NEUTRO, cancelable = false),
    VENCIDA("Vencida", TonoEstado.NEUTRO, cancelable = false);

    companion object {
        fun desde(valor: String?): EstadoReserva? =
            entries.firstOrNull { it.name.equals(valor?.trim(), ignoreCase = true) }

        // Nombre para mostrar; si llega un estado desconocido se muestra tal cual
        fun nombreDe(valor: String?): String = desde(valor)?.nombre ?: (valor ?: "")
    }
}

object ReglasReserva {
    // H73, H76: texto bajo el código. Solo una reserva activa se muestra en la botica
    fun mensajeCodigo(estado: String?): String = when (EstadoReserva.desde(estado)) {
        EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA, EstadoReserva.LISTA -> "Muestra este código en la botica"
        EstadoReserva.RECHAZADA -> "Esta reserva fue rechazada"
        EstadoReserva.CANCELADA -> "Cancelaste esta reserva"
        EstadoReserva.VENCIDA -> "Esta reserva venció"
        EstadoReserva.ENTREGADA -> "Ya recogiste este pedido"
        null -> "Esta reserva ya no está activa"
    }

    // H73, H76: la tarjeta del código usa el verde azulado solo si la reserva sigue activa
    fun tonoTarjetaCodigo(estado: String?): TonoEstado =
        if (EstadoReserva.desde(estado)?.cancelable == true) TonoEstado.PRIMARIO else TonoEstado.NEUTRO

    // H72: aviso de la receta en la reserva, solo si es CON_RECETA y sigue activa
    fun mostrarAvisoReceta(estado: String?, condicionVenta: String?): Boolean =
        EstadoReserva.desde(estado)?.cancelable == true &&
            ReglasCondicionVenta.pideReceta(condicionVenta)

    // H75: solo se cancela desde PENDIENTE, CONFIRMADA o LISTA y si venceEn no pasó
    // (el servidor responde 409 "La reserva ya venció" aunque aún no la haya marcado VENCIDA)
    fun puedeCancelar(estado: String?, venceEn: String?, ahoraMilis: Long): Boolean {
        if (EstadoReserva.desde(estado)?.cancelable != true) return false
        val vence = FechaLima.aMilis(venceEn) ?: return true
        return vence > ahoraMilis
    }

    // H76: de la más reciente a la más antigua por creadoEn
    fun <T> ordenarRecientes(reservas: List<T>, creadoEn: (T) -> String?): List<T> =
        reservas.sortedByDescending { FechaLima.aMilis(creadoEn(it)) ?: Long.MIN_VALUE }
}
