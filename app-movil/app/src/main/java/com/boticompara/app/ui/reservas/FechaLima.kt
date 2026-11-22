package com.boticompara.app.ui.reservas

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// H73, H76: la API envía las fechas en UTC ("2026-11-13T15:30:00.123456Z"); la app las muestra
// en hora de Lima. Se usa java.util porque java.time no está en Android 7 (minSdk 24).
object FechaLima {
    private val UTC = TimeZone.getTimeZone("UTC")
    private val LIMA = TimeZone.getTimeZone("America/Lima")
    private val ISO = Regex(
        """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?(Z|[+-]\d{2}:?\d{2})?$"""
    )

    // Milisegundos desde 1970 (UTC), o null si el texto no es una fecha ISO 8601
    fun aMilis(iso: String?): Long? {
        val m = ISO.matchEntire(iso?.trim() ?: return null) ?: return null
        val g = m.groupValues
        val calendario = Calendar.getInstance(UTC).apply {
            clear()
            set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
        }
        val milisFraccion = g[7].padEnd(3, '0').take(3).toInt()
        return calendario.timeInMillis + milisFraccion - desfaseMilis(g[8])
    }

    // "13/11/2026, 10:30 a. m." en hora de Lima; si no se puede leer, el texto original
    fun formatear(iso: String?): String {
        val milis = aMilis(iso) ?: return iso ?: ""
        return formatear(milis)
    }

    fun formatear(milis: Long): String {
        val c = Calendar.getInstance(LIMA).apply { timeInMillis = milis }
        val hora24 = c.get(Calendar.HOUR_OF_DAY)
        val hora12 = if (hora24 % 12 == 0) 12 else hora24 % 12
        val sufijo = if (hora24 < 12) "a. m." else "p. m."
        return String.format(
            Locale.US,
            "%02d/%02d/%04d, %d:%02d %s",
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.YEAR),
            hora12,
            c.get(Calendar.MINUTE),
            sufijo
        )
    }

    private fun desfaseMilis(zona: String): Long {
        if (zona.isEmpty() || zona == "Z") return 0
        val signo = if (zona[0] == '-') -1 else 1
        val digitos = zona.substring(1).replace(":", "")
        val horas = digitos.take(2).toInt()
        val minutos = digitos.drop(2).ifEmpty { "0" }.toInt()
        return signo * (horas * 60L + minutos) * 60_000L
    }
}
