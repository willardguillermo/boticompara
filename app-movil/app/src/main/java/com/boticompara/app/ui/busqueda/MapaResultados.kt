package com.boticompara.app.ui.busqueda

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boticompara.app.data.remote.dto.ProductoResponse
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.animacionesApagadas
import com.boticompara.app.ui.navigation.PausarDesenfoqueBarra
import com.boticompara.app.ui.theme.Dimens
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch

private const val ZOOM_INICIAL = 12f
private const val ZOOM_USUARIO = 15f
// Margen alrededor de los puntos de la vista inicial: el pin se dibuja hacia arriba desde la botica
// (con la etiqueta "Más barato" mide ~60 dp) y hacia los lados (~45 dp), así que el margen debe cubrirlo
private const val MARGEN_VISTA_DP = 72

// Pines con colores fijos de la marca: se leen igual sobre el mapa claro y el oscuro
private val VerdeMarca = Color(0xFF006A60)
private val VerdeMarcaSeleccionado = Color(0xFF00433C)
private val AzulUbicacion = Color(0xFF1A73E8)

internal data class MarcadorBotica(
    val producto: ProductoResponse,
    val lat: Double,
    val lng: Double
)

internal fun agruparPorBotica(productos: List<ProductoResponse>): List<MarcadorBotica> =
    productos
        .filter { it.boticaLatitud != null && it.boticaLongitud != null }
        .groupBy { it.boticaId }
        .map { (_, delaBotica) ->
            val masBarato = delaBotica.minByOrNull { it.precio }!!
            MarcadorBotica(masBarato, masBarato.boticaLatitud!!, masBarato.boticaLongitud!!)
        }

private fun Punto.aLatLng() = LatLng(lat, lng)

/**
 * H48 - Mapa de resultados. Abre centrado en el usuario (GPS, ubicación manual o Lima centro, ver
 * [elegirUbicacionInicial]) con zoom ~15 y deja a la vista las boticas cercanas. Cada botica es un pin con
 * su precio; la más barata va resaltada. Al tocar un pin aparece una tarjeta con la botica, el distrito,
 * el precio, la distancia y el botón para ver el detalle.
 * [espacioInferior] es lo que tapa la barra de navegación flotante: el mapa sigue debajo de ella,
 * pero el logo de Google, el centro de la vista y la tarjeta quedan por encima.
 */
@Composable
fun MapaResultados(
    productos: List<ProductoResponse>,
    onProductoClick: (productoId: Long) -> Unit,
    modifier: Modifier = Modifier,
    ubicacion: UbicacionInicial? = null,
    espacioInferior: Dp = 0.dp
) {
    val alTocar by rememberUpdatedState(onProductoClick)
    // El mapa es un SurfaceView: con la pantalla grabada para el desenfoque de la barra no se vería
    PausarDesenfoqueBarra()
    val sinAnimacion = animacionesApagadas()
    val densidad = LocalDensity.current

    val marcadores = remember(productos) { agruparPorBotica(productos) }
    val masBaratas = remember(marcadores) { boticasMasBaratas(marcadores) }
    val sinUbicacion = remember(productos) {
        productos.map { it.boticaId }.distinct().size - marcadores.size
    }

    var seleccionadaId by remember { mutableStateOf<Long?>(null) }
    val seleccionada = marcadores.firstOrNull { it.producto.boticaId == seleccionadaId }
    var tamanoMapa by remember { mutableStateOf(IntSize.Zero) }
    // El usuario movió el mapa con el dedo: ya no se recentra solo (sí con el botón)
    var movidoPorUsuario by remember { mutableStateOf(false) }
    var pedidosDeCentrado by remember { mutableIntStateOf(0) }

    val camara = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(CENTRO_LIMA_PUNTO.aLatLng(), ZOOM_INICIAL)
    }

    LaunchedEffect(camara.isMoving) {
        if (camara.isMoving && camara.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            movidoPorUsuario = true
        }
    }

    var yaCentrado by remember { mutableStateOf(false) }
    LaunchedEffect(ubicacion, marcadores, tamanoMapa, pedidosDeCentrado) {
        val actual = ubicacion ?: return@LaunchedEffect
        if (tamanoMapa.width == 0 || tamanoMapa.height == 0) return@LaunchedEffect
        if (movidoPorUsuario) return@LaunchedEffect
        val margenPx = with(densidad) { MARGEN_VISTA_DP.dp.roundToPx() }
            .coerceAtMost(minOf(tamanoMapa.width, tamanoMapa.height) / 4)
        centrar(
            camara = camara,
            usuario = actual.punto,
            boticas = marcadores.map { Punto(it.lat, it.lng) },
            masBaratas = marcadores.filter { it.producto.boticaId in masBaratas }.map { Punto(it.lat, it.lng) },
            margenPx = margenPx,
            animar = yaCentrado && !sinAnimacion
        )
        yaCentrado = true
    }

    val oscuro = isSystemInDarkTheme()
    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { if (it != tamanoMapa) tamanoMapa = it },
            cameraPositionState = camara,
            // Arriba, los avisos; a la derecha, la columna de botones; abajo, la barra flotante.
            // La vista inicial se ajusta dentro de ese hueco, así nada de eso tapa un pin.
            contentPadding = PaddingValues(top = 56.dp, end = 60.dp, bottom = espacioInferior),
            mapColorScheme = if (oscuro) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapClick = { seleccionadaId = null }
        ) {
            // Una sola ubicación del usuario: el punto azul si es el GPS, el marcador claro si es la elegida
            val tipoUsuario = ubicacion?.let { marcadorUsuario(it.origen) }
            if (ubicacion != null && tipoUsuario != null) {
                val esGps = tipoUsuario == MarcadorUsuario.PUNTO_GPS
                MarkerComposable(
                    tipoUsuario,
                    state = rememberUpdatedMarkerState(position = ubicacion.punto.aLatLng()),
                    contentDescription = if (esGps) "Tu ubicación" else "Tu ubicación elegida",
                    title = if (esGps) "Tu ubicación" else "Tu ubicación elegida",
                    // Centrado en la ubicación: los pines crecen hacia arriba desde su punta, así que aunque
                    // una botica esté en el mismo punto, el marcador del usuario asoma debajo de su precio
                    anchor = Offset(0.5f, 0.5f),
                    // Debajo de los pines: si se superponen, el precio siempre se lee
                    zIndex = 0f,
                    onClick = { true }
                ) {
                    if (esGps) PuntoUbicacion() else MarcadorUbicacionElegida()
                }
            }
            marcadores.forEach { marcador ->
                val id = marcador.producto.boticaId
                val barata = id in masBaratas
                val elegida = id == seleccionadaId
                val precio = formatoPrecio(marcador.producto.precio)
                key(id) {
                    MarkerComposable(
                        precio, barata, elegida,
                        state = rememberUpdatedMarkerState(position = LatLng(marcador.lat, marcador.lng)),
                        contentDescription = buildString {
                            append(marcador.producto.boticaNombre).append(", ").append(precio)
                            if (barata) append(", más barato")
                        },
                        title = marcador.producto.boticaNombre,
                        zIndex = when {
                            elegida -> 3f
                            barata -> 2f
                            else -> 1f
                        },
                        onClick = {
                            seleccionadaId = id
                            true
                        }
                    ) {
                        PinPrecio(precio = precio, masBarato = barata, seleccionado = elegida)
                    }
                }
            }
        }

        // Arriba: de dónde salió la ubicación y cuántas boticas no salen en el mapa
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Dimens.Space12)
                .padding(end = 56.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ubicacion?.let { AvisoMapa(textoOrigen(it.origen)) }
            if (sinUbicacion > 0) {
                AvisoMapa(
                    if (sinUbicacion == 1) "1 botica sin ubicación registrada no aparece en el mapa"
                    else "$sinUbicacion boticas sin ubicación registrada no aparecen en el mapa"
                )
            }
        }

        // Centrar, acercar y alejar, en una columna (el pellizco y el doble toque siguen funcionando)
        val alcance = rememberCoroutineScope()
        fun zoom(cambio: CameraUpdate) {
            movidoPorUsuario = true
            alcance.launch { if (sinAnimacion) camara.move(cambio) else camara.animate(cambio, 250) }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Dimens.Space12),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8)
        ) {
            if (ubicacion != null) {
                BotonMapa(Icons.Rounded.MyLocation, "Centrar el mapa en tu ubicación") {
                    movidoPorUsuario = false
                    pedidosDeCentrado++
                }
            }
            BotonMapa(Icons.Rounded.Add, "Acercar") { zoom(CameraUpdateFactory.zoomIn()) }
            BotonMapa(Icons.Rounded.Remove, "Alejar") { zoom(CameraUpdateFactory.zoomOut()) }
        }

        seleccionada?.let { marcador ->
            val distancia = if (ubicacion?.esDelUsuario == true) {
                distanciaKm(ubicacion.punto, Punto(marcador.lat, marcador.lng))
            } else {
                marcador.producto.distanciaKm
            }
            TarjetaBotica(
                producto = marcador.producto,
                distanciaKm = distancia,
                masBarato = marcador.producto.boticaId in masBaratas,
                onVerDetalle = { alTocar(marcador.producto.productoId) },
                onCerrar = { seleccionadaId = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Dimens.Space12)
                    .padding(bottom = espacioInferior + Dimens.Space12)
            )
        }
    }
}

/**
 * Centra en el usuario con zoom [ZOOM_USUARIO] y abre la vista lo justo para que entren completas las
 * boticas cercanas y la más barata, sin pasar de ese zoom y sin dejar de estar centrada en el usuario.
 */
private suspend fun centrar(
    camara: CameraPositionState,
    usuario: Punto,
    boticas: List<Punto>,
    masBaratas: List<Punto>,
    margenPx: Int,
    animar: Boolean
) {
    fun cambioPara(puntos: List<Punto>) = limitesCentrados(usuario, puntos)?.let { limites ->
        CameraUpdateFactory.newLatLngBounds(
            LatLngBounds(limites.first.aLatLng(), limites.second.aLatLng()),
            margenPx
        )
    } ?: CameraUpdateFactory.newLatLngZoom(usuario.aLatLng(), ZOOM_USUARIO)

    val cambio = cambioPara(puntosVistaInicial(usuario, boticas, masBaratas))
    if (animar) camara.animate(cambio, 600) else camara.move(cambio)
    if (camara.position.zoom > ZOOM_USUARIO) {
        camara.move(CameraUpdateFactory.newLatLngZoom(usuario.aLatLng(), ZOOM_USUARIO))
    }
}

private fun textoOrigen(origen: OrigenUbicacion) = when (origen) {
    OrigenUbicacion.GPS -> "Tu ubicación (GPS)"
    OrigenUbicacion.MANUAL -> "Tu ubicación elegida"
    OrigenUbicacion.POR_DEFECTO -> "Lima centro: elige tu ubicación para ver las más cercanas"
}

@Composable
private fun BotonMapa(icono: ImageVector, descripcion: String, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(icono, contentDescription = descripcion)
    }
}

@Composable
private fun AvisoMapa(texto: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** El punto azul de "Tu ubicación": centro azul con borde blanco y un halo suave. */
@Composable
private fun PuntoUbicacion() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(AzulUbicacion.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color.White, CircleShape)
                .padding(3.dp)
                .background(AzulUbicacion, CircleShape)
        )
    }
}

/**
 * La ubicación elegida a mano: un marcador claro y compacto (círculo blanco con borde e ícono verde
 * azulado) para que no se confunda con el punto azul del GPS ni con los pines de precio, que son
 * píldoras verdes. El aviso de arriba del mapa dice qué es.
 */
@Composable
private fun MarcadorUbicacionElegida() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(Color.White, CircleShape)
            .border(BorderStroke(2.5.dp, VerdeMarca), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = VerdeMarca,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Pin con el precio. Normal: píldora verde azulado con texto blanco. El más barato: tono invertido
 * (blanco con borde y texto verde) y la etiqueta "Más barato" encima. El seleccionado, más oscuro.
 */
@Composable
private fun PinPrecio(precio: String, masBarato: Boolean, seleccionado: Boolean) {
    val fondo = when {
        seleccionado -> VerdeMarcaSeleccionado
        masBarato -> Color.White
        else -> VerdeMarca
    }
    val texto = if (masBarato && !seleccionado) VerdeMarca else Color.White
    val borde = if (masBarato) VerdeMarca else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (masBarato) {
            Row(
                modifier = Modifier
                    .background(VerdeMarca, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Savings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text("Más barato", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))
        }
        Box(
            modifier = Modifier
                .background(fondo, RoundedCornerShape(50))
                .border(BorderStroke(if (masBarato) 2.dp else 1.5.dp, borde), RoundedCornerShape(50))
                .padding(horizontal = if (seleccionado) 12.dp else 10.dp, vertical = if (seleccionado) 6.dp else 4.dp)
        ) {
            Text(
                precio,
                color = texto,
                fontSize = if (seleccionado) 15.sp else 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        // La punta del pin, apuntando a la botica
        val colorPunta = if (masBarato && !seleccionado) VerdeMarca else fondo
        Canvas(modifier = Modifier.size(width = 12.dp, height = 7.dp)) {
            val punta = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(punta, colorPunta)
        }
    }
}

@Composable
private fun TarjetaBotica(
    producto: ProductoResponse,
    distanciaKm: Double?,
    masBarato: Boolean,
    onVerDetalle: () -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(Dimens.Space16)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        producto.boticaNombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(producto.boticaDistrito, distanciaKm?.let { "a ${formatoDistancia(it)}" })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCerrar) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                }
            }
            Spacer(Modifier.height(Dimens.Space8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${producto.nombreComercial} · ${producto.presentacion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (masBarato) {
                        Text(
                            "Más barato",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(Dimens.Space12))
                Text(
                    formatoPrecio(producto.precio),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(Dimens.Space12))
            BotiButton(
                text = "Ver detalle",
                onClick = onVerDetalle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
