package com.boticompara.app.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

// Obtiene la ubicación del celular (H27), sin librerías extra
class UbicacionProvider(private val context: Context) {

    data class Coordenadas(val lat: Double, val lng: Double)

    private fun permitido(permiso: String) =
        ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED

    fun tienePermiso(): Boolean =
        permitido(Manifest.permission.ACCESS_FINE_LOCATION) ||
                permitido(Manifest.permission.ACCESS_COARSE_LOCATION)

    // null si no hay permiso, el GPS está apagado o no se pudo obtener.
    // El permiso se comprueba aquí mismo, justo antes de usarlo (lint, MissingPermission), y además se
    // captura SecurityException: el usuario puede quitarlo desde Ajustes mientras se espera la ubicación.
    suspend fun obtener(): Coordenadas? {
        val fino = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val grueso = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!fino && !grueso) return null
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val activos = manager.getProviders(true)
            if (activos.isEmpty()) return null

            // 1. Pedir una ubicación nueva (Android 11 o más)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val proveedor = when {
                    fino && LocationManager.GPS_PROVIDER in activos -> LocationManager.GPS_PROVIDER
                    LocationManager.NETWORK_PROVIDER in activos -> LocationManager.NETWORK_PROVIDER
                    else -> activos.first()
                }
                val nueva = suspendCancellableCoroutine<Coordenadas?> { cont ->
                    val senal = CancellationSignal()
                    cont.invokeOnCancellation { senal.cancel() }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    try {
                        manager.getCurrentLocation(proveedor, senal, ContextCompat.getMainExecutor(context)) { loc ->
                            if (cont.isActive) cont.resume(loc?.let { Coordenadas(it.latitude, it.longitude) })
                        }
                    } catch (e: SecurityException) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                if (nueva != null) return nueva
            }

            // 2. Si no hubo, usar la última conocida
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) return null
            val ultima = activos
                .mapNotNull { proveedor ->
                    try {
                        manager.getLastKnownLocation(proveedor)
                    } catch (e: SecurityException) {
                        null
                    }
                }
                .maxByOrNull { it.time }
            return ultima?.let { Coordenadas(it.latitude, it.longitude) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
    }
}
