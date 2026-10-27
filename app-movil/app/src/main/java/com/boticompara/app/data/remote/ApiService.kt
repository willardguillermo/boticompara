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

// ---- Recuperar contraseña (H17) ----
data class RecuperarRequest(val correo: String)
data class RestablecerRequest(val correo: String, val token: String, val password: String)
data class MensajeResponse(val mensaje: String)

// ---- Perfil (H16) ----
data class PerfilResponse(
    val id: Long, val nombre: String, val correo: String,
    val telefono: String?, val direccion: String?, val rol: String
)
data class PerfilRequest(val nombre: String, val telefono: String?, val direccion: String?)

// ---- Búsqueda (H22–H27) ----
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
    val boticaDistrito: String,
    val distanciaKm: Double? = null   // H27: null si no se envió la ubicación
)

// ---- Detalle de Producto (H28) ----
data class ProductoDetalleResponse(
    val productoId: Long,
    val nombreComercial: String,
    val principioActivo: String,
    val presentacion: String,
    val precio: Double,
    val stock: Int,
    val boticaId: Long,
    val boticaNombre: String,
    val boticaDireccion: String,
    val boticaDistrito: String,
    val boticaTelefono: String? = null,   // null si la botica no lo registró
    val fabricante: String? = null,
    val requerimientoReceta: Boolean? = false
)

// ---- Alternativas del producto (H36, H37, H41) ----
data class AlternativaResponse(
    val productoId: Long,
    val nombreComercial: String,
    val principioActivo: String,
    val presentacion: String,
    val precio: Double,
    val stock: Int,
    val boticaId: Long,
    val boticaNombre: String,
    val boticaDireccion: String,
    val boticaDistrito: String,
    val tipoCoincidencia: String,          // "EQUIVALENTE" o "PRINCIPIO_ACTIVO"
    val ahorroPorcentaje: Double? = null   // H37: null si no es más barata que el consultado
)

data class AlternativasResponse(
    val productoId: Long,
    val sinEquivalentes: Boolean,          // H41: true cuando no hay alternativas
    val alternativas: List<AlternativaResponse> = emptyList()
)

// ---- Favoritos (H43, H44, H45) ----
data class FavoritoRequest(val productoId: Long)

data class FavoritoResponse(
    val productoId: Long,
    val nombreComercial: String,
    val principioActivo: String,
    val presentacion: String,
    val precio: Double,
    val stock: Int,
    val boticaId: Long,
    val boticaNombre: String,
    val boticaDireccion: String,
    val boticaDistrito: String,
    val fecha: String                      // fecha en que se guardó (ISO 8601)
)

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/registro")   // 201, devuelve el usuario SIN token -> luego llamar a login
    suspend fun registro(@Body request: RegistroRequest): Response<UsuarioResponse>

    @POST("auth/recuperar")   // H17: siempre responde 200, exista o no el correo
    suspend fun recuperar(@Body request: RecuperarRequest): Response<MensajeResponse>

    @POST("auth/restablecer")   // H17: 200 si el código es válido, 400 si no
    suspend fun restablecer(@Body request: RestablecerRequest): Response<MensajeResponse>

    @GET("usuarios/me")
    suspend fun obtenerPerfil(@Header("Authorization") auth: String): Response<PerfilResponse>

    @PUT("usuarios/me")
    suspend fun actualizarPerfil(
        @Header("Authorization") auth: String,
        @Body request: PerfilRequest
    ): Response<PerfilResponse>

    @DELETE("usuarios/me")   // H19: 204 sin cuerpo, la cuenta queda anonimizada
    suspend fun eliminarCuenta(@Header("Authorization") auth: String): Response<Unit>

    @GET("productos/buscar")
    suspend fun buscarProductos(
        @Header("Authorization") auth: String,
        @Query("q") query: String,                 // mínimo 2 caracteres
        @Query("tipo") tipo: String = "nombre",    // "nombre" o "principio"
        @Query("orden") orden: String = "precio_asc",
        @Query("lat") lat: Double? = null,         // H27: van juntos o ninguno
        @Query("lng") lng: Double? = null,
        @Query("radioKm") radioKm: Double? = null  // H27: mayor que 0 y hasta 50
    ): Response<List<ProductoResponse>>

    // H28: Detalle del producto por ID
    @GET("productos/{id}")
    suspend fun obtenerDetalleProducto(
        @Header("Authorization") auth: String,
        @Path("id") id: Long
    ): Response<ProductoDetalleResponse>

    // H36, H37, H41: alternativas del producto (público; con token queda registrado el usuario, H42)
    @GET("productos/{id}/alternativas")
    suspend fun obtenerAlternativas(
        @Header("Authorization") auth: String,
        @Path("id") id: Long
    ): Response<AlternativasResponse>

    // H43: guardar un favorito (201 nuevo, 200 si ya estaba)
    @POST("favoritos")
    suspend fun agregarFavorito(
        @Header("Authorization") auth: String,
        @Body request: FavoritoRequest
    ): Response<FavoritoResponse>

    // H44: lista de favoritos, del más reciente al más antiguo
    @GET("favoritos")
    suspend fun obtenerFavoritos(
        @Header("Authorization") auth: String
    ): Response<List<FavoritoResponse>>

    // H45: quitar un favorito (204 sin cuerpo, también si no estaba guardado)
    @DELETE("favoritos/{productoId}")
    suspend fun quitarFavorito(
        @Header("Authorization") auth: String,
        @Path("productoId") productoId: Long
    ): Response<Unit>
}