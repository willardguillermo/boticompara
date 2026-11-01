package com.boticompara.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// DataStore propio: así cerrar sesión no borra la ubicación elegida
private val Context.ubicacionDataStore by preferencesDataStore(name = "ubicacion_manual")

// Ubicación elegida por el comprador (distrito o punto en el mapa) cuando no usa el GPS (H50)
data class UbicacionManual(
    val etiqueta: String,
    val lat: Double,
    val lng: Double
)

class UbicacionManualStore(private val context: Context) {

    companion object {
        private val ETIQUETA_KEY = stringPreferencesKey("etiqueta")
        private val LAT_KEY = doublePreferencesKey("lat")
        private val LNG_KEY = doublePreferencesKey("lng")
    }

    // null si el comprador nunca eligió una ubicación manual
    suspend fun obtener(): UbicacionManual? {
        val datos = context.ubicacionDataStore.data.first()
        val etiqueta = datos[ETIQUETA_KEY]
        val lat = datos[LAT_KEY]
        val lng = datos[LNG_KEY]
        return if (etiqueta != null && lat != null && lng != null) {
            UbicacionManual(etiqueta, lat, lng)
        } else {
            null
        }
    }

    suspend fun guardar(ubicacion: UbicacionManual) {
        context.ubicacionDataStore.edit {
            it[ETIQUETA_KEY] = ubicacion.etiqueta
            it[LAT_KEY] = ubicacion.lat
            it[LNG_KEY] = ubicacion.lng
        }
    }

    suspend fun borrar() {
        context.ubicacionDataStore.edit { it.clear() }
    }
}