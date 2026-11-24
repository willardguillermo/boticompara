package com.boticompara.app.data.remote

import com.boticompara.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    // H67: inicia sesión con Google (o crea la cuenta de comprador). Devuelve nuestro JWT, igual que el login
    @POST("auth/google")
    suspend fun loginGoogle(@Body request: GoogleAuthRequest): Response<AuthResponse>

    @POST("auth/registro")   // 201, devuelve el usuario SIN token -> luego llamar a login
    suspend fun registro(@Body request: RegistroRequest): Response<UsuarioResponse>

    @POST("auth/recuperar")   // H17: siempre responde 200, exista o no el correo
    suspend fun recuperar(@Body request: RecuperarRequest): Response<MensajeResponse>

    @POST("auth/restablecer")   // H17: 200 si el código es válido, 400 si no
    suspend fun restablecer(@Body request: RestablecerRequest): Response<MensajeResponse>

    // H70: asistente de texto. El servidor no guarda la conversación: la app envía el historial
    @POST("chat")
    suspend fun enviarMensajeChat(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @GET("usuarios/me")
    suspend fun obtenerPerfil(@Header("Authorization") auth: String): Response<PerfilResponse>

    @PUT("usuarios/me")
    suspend fun actualizarPerfil(
        @Header("Authorization") auth: String,
        @Body request: PerfilRequest
    ): Response<PerfilResponse>

    @PUT("usuarios/me/notificaciones")   // H66: activa o desactiva las alertas de baja de precio
    suspend fun actualizarNotificaciones(
        @Header("Authorization") auth: String,
        @Body request: NotificacionesRequest
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

    // H60: poner, cambiar o quitar (null) el precio objetivo de un favorito
    @PUT("favoritos/{productoId}/precio-objetivo")
    suspend fun actualizarPrecioObjetivo(
        @Header("Authorization") auth: String,
        @Path("productoId") productoId: Long,
        @Body request: PrecioObjetivoRequest
    ): Response<FavoritoResponse>

    // H59: alertas de baja de precio, de la más reciente a la más antigua (máx. 100)
    @GET("alertas")
    suspend fun obtenerAlertas(
        @Header("Authorization") auth: String,
        @Query("soloNoLeidas") soloNoLeidas: Boolean = true
    ): Response<List<AlertaResponse>>

    // H59: número para el badge del ícono de Alertas
    @GET("alertas/resumen")
    suspend fun obtenerResumenAlertas(
        @Header("Authorization") auth: String
    ): Response<ResumenAlertasResponse>

    // H59: marcar una alerta como leída (204, también si ya estaba leída)
    @PUT("alertas/{id}/leida")
    suspend fun marcarAlertaLeida(
        @Header("Authorization") auth: String,
        @Path("id") id: Long
    ): Response<Unit>

    // H59: marcar todas las alertas como leídas (204)
    @PUT("alertas/leidas")
    suspend fun marcarTodasLeidas(
        @Header("Authorization") auth: String
    ): Response<Unit>

    // H72, H73, H74: reservar un producto (201 con la reserva PENDIENTE y su código)
    @POST("reservas")
    suspend fun crearReserva(
        @Header("Authorization") auth: String,
        @Body request: ReservaRequest
    ): Response<ReservaResponse>

    // H76: historial de reservas del comprador, de la más reciente a la más antigua (máx. 100)
    @GET("reservas/mias")
    suspend fun obtenerMisReservas(
        @Header("Authorization") auth: String
    ): Response<List<ReservaResponse>>

    // H76: detalle de una reserva propia
    @GET("reservas/{id}")
    suspend fun obtenerReserva(
        @Header("Authorization") auth: String,
        @Path("id") id: Long
    ): Response<ReservaResponse>

    // H75: cancelar una reserva PENDIENTE, CONFIRMADA o LISTA (sin cuerpo)
    @PATCH("reservas/{id}/cancelar")
    suspend fun cancelarReserva(
        @Header("Authorization") auth: String,
        @Path("id") id: Long
    ): Response<ReservaResponse>

    @GET("boticas/{id}/resenas")
    suspend fun obtenerResenas(
        @Path("id") id: Long,
        @Query("page") page: Int = 0
    ): Response<ResenasResponse>

    @PUT("boticas/{id}/resenas/mia")
    suspend fun guardarResena(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: ResenaRequest
    ): Response<ResenaResponse>
}
