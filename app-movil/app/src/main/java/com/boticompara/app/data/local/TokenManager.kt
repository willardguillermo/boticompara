package com.boticompara.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

val Context.dataStore by preferencesDataStore(name = "settings")

class TokenManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "boticompara_jwt_key"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 128
        private val TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val TOKEN_CIFRADO_KEY = stringPreferencesKey("jwt_token_cifrado")
        private val NOMBRE_KEY = stringPreferencesKey("usuario_nombre")
        private val ROL_KEY = stringPreferencesKey("usuario_rol")
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    init {
        runBlocking {
            val tokenPlano = context.dataStore.data.first()[TOKEN_KEY]
            if (tokenPlano != null) {
                val cifrado = runCatching { cifrar(tokenPlano) }.getOrNull()
                if (cifrado != null) {
                    context.dataStore.edit {
                        it[TOKEN_CIFRADO_KEY] = cifrado
                        it.remove(TOKEN_KEY)
                    }
                }
            }
        }
    }

    // Para observar si hay sesión (ej. decidir si mostrar Login o Búsqueda)
    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_CIFRADO_KEY]?.let(::descifrar) ?: prefs[TOKEN_KEY]
    }
    val nombreFlow: Flow<String?> = context.dataStore.data.map { it[NOMBRE_KEY] }

    // Guardar después de un login exitoso (H18)
    suspend fun saveSession(token: String, nombre: String, rol: String) {
        val tokenCifrado = cifrar(token)
        context.dataStore.edit {
            it[TOKEN_CIFRADO_KEY] = tokenCifrado
            it[NOMBRE_KEY] = nombre
            it[ROL_KEY] = rol
            it.remove(TOKEN_KEY)
        }
    }

    // Header listo para Retrofit: "Bearer eyJ..."
    suspend fun getBearer(): String {
        val token = tokenFlow.first() ?: ""
        return "Bearer $token"
    }

    // Cerrar sesión
    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    private fun secretKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generador.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generador.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun cifrar(texto: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cifrado = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        val bytes = ByteArray(iv.size + cifrado.size)
        iv.copyInto(bytes)
        cifrado.copyInto(bytes, iv.size)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun descifrar(datos: String): String? = try {
        val bytes = Base64.decode(datos, Base64.NO_WRAP)
        if (bytes.size <= IV_LENGTH) null else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_LENGTH, bytes, 0, IV_LENGTH)
            )
            String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
        }
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
