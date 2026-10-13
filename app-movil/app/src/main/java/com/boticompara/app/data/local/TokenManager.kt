package com.boticompara.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val NOMBRE_KEY = stringPreferencesKey("usuario_nombre")
        private val ROL_KEY = stringPreferencesKey("usuario_rol")
    }

    // Para observar si hay sesión (ej. decidir si mostrar Login o Búsqueda)
    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val nombreFlow: Flow<String?> = context.dataStore.data.map { it[NOMBRE_KEY] }

    // Guardar después de un login exitoso (H18)
    suspend fun saveSession(token: String, nombre: String, rol: String) {
        context.dataStore.edit {
            it[TOKEN_KEY] = token
            it[NOMBRE_KEY] = nombre
            it[ROL_KEY] = rol
        }
    }

    // Header listo para Retrofit: "Bearer eyJ..."
    suspend fun getBearer(): String {
        val token = context.dataStore.data.first()[TOKEN_KEY] ?: ""
        return "Bearer $token"
    }

    // Cerrar sesión
    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}