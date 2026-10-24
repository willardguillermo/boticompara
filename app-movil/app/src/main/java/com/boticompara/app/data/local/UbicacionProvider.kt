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

    // null si no hay permiso, el GPS está apagado o no se pudo obtener
    suspend fun obtener(): Coordenadas? {
        if (!tienePermiso()) return null
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val activos = manager.getProviders(true)
            if (activos.isEmpty()) return null

            // 1. Pedir una ubicación nueva (Android 11 o más)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val proveedor = when {
                    permitido(Manifest.permission.ACCESS_FINE_LOCATION) &&
                            LocationManager.GPS_PROVIDER in activos -> LocationManager.GPS_PROVIDER
                    LocationManager.NETWORK_PROVIDER in activos -> LocationManager.NETWORK_PROVIDER
                    else -> activos.first()
                }
                val nueva = suspendCancellableCoroutine<Coordenadas?> { cont ->
                    val senal = CancellationSignal()
                    cont.invokeOnCancellation { senal.cancel() }
                    manager.getCurrentLocation(proveedor, senal, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc?.let { Coordenadas(it.latitude, it.longitude) })
                    }
                }
                if (nueva != null) return nueva
            }

            // 2. Si no hubo, usar la última conocida
            val ultima = activos
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            return ultima?.let { Coordenadas(it.latitude, it.longitude) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
    }
}