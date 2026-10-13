package com.boticompara.app.data.remote

import retrofit2.Response
import retrofit2.http.*

// ---- Autenticación ----
data class LoginRequest(val correo: String, val password: String)
data class UsuarioResponse(val id: Long, val nombre: String, val correo: String, val rol: String)
data class AuthResponse(val token: String, val tipo: String, val usuario: UsuarioResponse)
data class RegistroRequest(
    val nombre: String,
    val correo: String,
    val password: String,          // mínimo 8 caracteres
    val telefono: String? = null,
    val rol: String = "COMPRADOR"
)

// ---- Perfil (H16) ----
data class PerfilResponse(
    val id: Long, val nombre: String, val correo: String,
    val telefono: String?, val direccion: String?, val rol: String
)
data class PerfilRequest(val nombre: String, val telefono: String?, val direccion: String?)

// ---- Búsqueda (H22–H24) ----
data class ProductoResponse(
    val productoId: Long,
    val nombreComercial: String,
    val principioActivo: String,
    val presentacion: String,
    val precio: Double,
    val stock: Int,
    val boticaId: Long,
    val boticaNombre: String,
    val boticaDireccion: String,
    val boticaDistrito: String
)

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/registro")   // 201, devuelve el usuario SIN token -> luego llamar a login
    suspend fun registro(@Body request: RegistroRequest): Response<UsuarioResponse>

    @GET("usuarios/me")
    suspend fun obtenerPerfil(@Header("Authorization") auth: String): Response<PerfilResponse>

    @PUT("usuarios/me")
    suspend fun actualizarPerfil(
        @Header("Authorization") auth: String,
        @Body request: PerfilRequest
    ): Response<PerfilResponse>

    @GET("productos/buscar")
    suspend fun buscarProductos(
        @Header("Authorization") auth: String,
        @Query("q") query: String,                 // mínimo 2 caracteres
        @Query("tipo") tipo: String = "nombre",    // "nombre" o "principio"
        @Query("orden") orden: String = "precio_asc"
    ): Response<List<ProductoResponse>>
}