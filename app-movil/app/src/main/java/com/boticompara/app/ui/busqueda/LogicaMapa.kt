package com.boticompara.app.ui.busqueda

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * H48 - Lógica del mapa de resultados, sin clases de Android ni de Google Maps para poder probarla
 * con JUnit: elegir dónde abre el mapa, qué boticas entran en la primera vista, cuál es la más barata
 * y cómo se escriben el precio y la distancia.
 */

/** Un punto en el mapa (latitud y longitud en grados). */
data class Punto(val lat: Double, val lng: Double)

internal val CENTRO_LIMA_PUNTO = Punto(-12.0464, -77.0300)

/** De dónde salió la ubicación con la que abre el mapa. */
enum class OrigenUbicacion { GPS, MANUAL, POR_DEFECTO }

data class UbicacionInicial(val punto: Punto, val origen: OrigenUbicacion) {
    /** Con Lima centro no hay "Tu ubicación": no se dibuja el punto azul ni se calculan distancias. */
    val esDelUsuario: Boolean get() = origen != OrigenUbicacion.POR_DEFECTO
}

/** Cómo se dibuja al usuario en el mapa. Es un solo valor: nunca hay dos ubicaciones del usuario a la vez. */
internal enum class MarcadorUsuario { PUNTO_GPS, UBICACION_ELEGIDA }

/** El punto azul con GPS; el marcador "Tu ubicación elegida" con la manual; nada con Lima centro. */
internal fun marcadorUsuario(origen: OrigenUbicacion): MarcadorUsuario? = when (origen) {
    OrigenUbicacion.GPS -> MarcadorUsuario.PUNTO_GPS
    OrigenUbicacion.MANUAL -> MarcadorUsuario.UBICACION_ELEGIDA
    OrigenUbicacion.POR_DEFECTO -> null
}

/**
 * Dónde abre el mapa: el GPS si dio permiso y hubo ubicación; si no, la ubicación manual guardada;
 * si no hay ninguna, Lima centro.
 */
internal fun elegirUbicacionInicial(gps: Punto?, manual: Punto?): UbicacionInicial = when {
    gps != null -> UbicacionInicial(gps, OrigenUbicacion.GPS)
    manual != null -> UbicacionInicial(manual, OrigenUbicacion.MANUAL)
    else -> UbicacionInicial(CENTRO_LIMA_PUNTO, OrigenUbicacion.POR_DEFECTO)
}

/** "S/ 12.50": siempre 2 decimales y punto decimal, como en el resto de la app. */
internal fun formatoPrecio(precio: Double): String = "S/ " + String.format(Locale.US, "%.2f", precio)

/** "850 m" por debajo de 1 km; "1.2 km" desde 1 km. */
internal fun formatoDistancia(km: Double): String =
    if (km < 1.0) "${max(1, (km * 1000).toInt())} m"
    else String.format(Locale.US, "%.1f km", km)

/** Distancia en línea recta (fórmula del haversine), en km. */
internal fun distanciaKm(a: Punto, b: Punto): Double {
    val radioTierraKm = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * radioTierraKm * asin(min(1.0, sqrt(h)))
}

/**
 * Las boticas con el precio más bajo (si empatan, todas): esas llevan el pin resaltado.
 * Lista vacía si no hay marcadores.
 */
internal fun boticasMasBaratas(marcadores: List<MarcadorBotica>): Set<Long> {
    val minimo = marcadores.minOfOrNull { it.producto.precio } ?: return emptySet()
    return marcadores.filter { it.producto.precio == minimo }.map { it.producto.boticaId }.toSet()
}

/** Radio en el que una botica cuenta como "cercana" para la primera vista del mapa. */
internal const val RADIO_CERCANAS_KM = 3.0

/**
 * Las boticas que deben verse al abrir el mapa: las que están a [RADIO_CERCANAS_KM] o menos.
 * Si ninguna lo está, la más cercana, para que al menos un pin quede a la vista.
 */
internal fun puntosCercanos(usuario: Punto, boticas: List<Punto>, radioKm: Double = RADIO_CERCANAS_KM): List<Punto> {
    if (boticas.isEmpty()) return emptyList()
    val cerca = boticas.filter { distanciaKm(usuario, it) <= radioKm }
    return cerca.ifEmpty { listOf(boticas.minBy { distanciaKm(usuario, it) }) }
}

/**
 * Los límites (suroeste, noreste) de una vista centrada en el usuario que contiene todos los [puntos]:
 * cada punto se refleja respecto al usuario, así el centro de la vista sigue siendo el usuario.
 * null si no hay puntos (entonces basta con el zoom fijo sobre el usuario).
 */
internal fun limitesCentrados(usuario: Punto, puntos: List<Punto>): Pair<Punto, Punto>? {
    if (puntos.isEmpty()) return null
    val dLat = puntos.maxOf { kotlin.math.abs(it.lat - usuario.lat) }
    val dLng = puntos.maxOf { kotlin.math.abs(it.lng - usuario.lng) }
    return Punto(usuario.lat - dLat, usuario.lng - dLng) to Punto(usuario.lat + dLat, usuario.lng + dLng)
}

/**
 * Los puntos que deben verse completos al abrir el mapa: las boticas cercanas ([puntosCercanos]) y,
 * aunque esté lejos, siempre la más barata (o las más baratas si empatan).
 */
internal fun puntosVistaInicial(usuario: Punto, boticas: List<Punto>, masBaratas: List<Punto>): List<Punto> =
    (puntosCercanos(usuario, boticas) + masBaratas).distinct()
