package com.boticompara.app.data.local

import java.text.Normalizer

/**
 * H50: distritos de Lima para la ubicación manual cuando el usuario no da permiso de GPS.
 * Son los mismos 35 distritos que ofrece la web (web-usuario/src/utils/distritos.js).
 *
 * Las coordenadas son APROXIMADAS: el centro o la zona más poblada de cada distrito
 * (plaza principal o municipalidad). Sirven para ordenar por cercanía, no para navegar.
 */
data class Distrito(val nombre: String, val lat: Double, val lng: Double)

object Distritos {

    val LISTA: List<Distrito> = listOf(
        Distrito("Ate", -12.0250, -76.9200),
        Distrito("Barranco", -12.1490, -77.0215),
        Distrito("Breña", -12.0590, -77.0520),
        Distrito("Carabayllo", -11.8960, -77.0330),
        Distrito("Chaclacayo", -11.9790, -76.7690),
        Distrito("Chorrillos", -12.1690, -77.0220),
        Distrito("Cieneguilla", -12.1100, -76.8150),
        Distrito("Comas", -11.9370, -77.0590),
        Distrito("El Agustino", -12.0430, -76.9950),
        Distrito("Independencia", -11.9910, -77.0540),
        Distrito("Jesús María", -12.0770, -77.0430),
        Distrito("La Molina", -12.0870, -76.9370),
        Distrito("La Victoria", -12.0660, -77.0180),
        Distrito("Lima", -12.0464, -77.0300),
        Distrito("Lince", -12.0840, -77.0360),
        Distrito("Los Olivos", -11.9910, -77.0700),
        Distrito("Lurigancho-Chosica", -11.9363, -76.6972),
        Distrito("Lurín", -12.2760, -76.8700),
        Distrito("Magdalena del Mar", -12.0910, -77.0700),
        Distrito("Miraflores", -12.1211, -77.0297),
        Distrito("Pueblo Libre", -12.0760, -77.0630),
        Distrito("Puente Piedra", -11.8660, -77.0760),
        Distrito("Rímac", -12.0360, -77.0290),
        Distrito("San Borja", -12.1050, -77.0000),
        Distrito("San Isidro", -12.0970, -77.0360),
        Distrito("San Juan de Lurigancho", -11.9960, -77.0050),
        Distrito("San Juan de Miraflores", -12.1570, -76.9720),
        Distrito("San Luis", -12.0750, -76.9950),
        Distrito("San Martín de Porres", -12.0120, -77.0600),
        Distrito("San Miguel", -12.0770, -77.0910),
        Distrito("Santa Anita", -12.0430, -76.9710),
        Distrito("Santiago de Surco", -12.1460, -77.0060),
        Distrito("Surquillo", -12.1130, -77.0200),
        Distrito("Villa El Salvador", -12.2130, -76.9370),
        Distrito("Villa María del Triunfo", -12.1610, -76.9400),
    )

    /** Busca por nombre sin distinguir mayúsculas ni tildes ("jesus maria" encuentra "Jesús María"). */
    fun buscar(nombre: String): Distrito? {
        val buscado = normalizar(nombre)
        if (buscado.isEmpty()) return null
        return LISTA.firstOrNull { normalizar(it.nombre) == buscado }
    }

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
}