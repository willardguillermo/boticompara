package com.boticompara.app.vistas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.boticompara.app.data.remote.ProductoResponse
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale

private val CENTRO_LIMA = GeoPoint(-12.0464, -77.0300)

// Un marcador por botica: se muestra el producto más barato de esa botica (H48)
internal data class MarcadorBotica(
    val producto: ProductoResponse,
    val lat: Double,
    val lng: Double
)

// Solo las boticas con coordenadas tienen marcador; el resto no se dibuja
internal fun agruparPorBotica(productos: List<ProductoResponse>): List<MarcadorBotica> =
    productos
        .filter { it.boticaLatitud != null && it.boticaLongitud != null }
        .groupBy { it.boticaId }
        .map { (_, delaBotica) ->
            val masBarato = delaBotica.minByOrNull { it.precio }!!
            MarcadorBotica(masBarato, masBarato.boticaLatitud!!, masBarato.boticaLongitud!!)
        }

// H48: mapa con un marcador por botica (nombre y precio); al tocarlo se abre el detalle (H28)
@Composable
fun MapaResultados(
    productos: List<ProductoResponse>,
    onProductoClick: (productoId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorFondo = MaterialTheme.colorScheme.primary.toArgb()
    val colorTexto = MaterialTheme.colorScheme.onPrimary.toArgb()
    val alTocar by rememberUpdatedState(onProductoClick)

    val marcadores = remember(productos) { agruparPorBotica(productos) }
    val sinUbicacion = remember(productos) {
        productos.map { it.boticaId }.distinct().size - marcadores.size
    }

    val mapView = remember {
        // osmdroid: identificarse ante el servidor de mapas y guardar la caché sin pedir permisos
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(CENTRO_LIMA)
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Cuando cambian los resultados, el mapa se ajusta para que se vean todas las boticas
    LaunchedEffect(marcadores) {
        mapView.post { ajustarVista(mapView, marcadores) }
    }

    Column(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            update = { vista ->
                vista.overlays.removeAll { it is Marker }
                marcadores.forEach { marcador ->
                    vista.overlays.add(
                        Marker(vista).apply {
                            position = GeoPoint(marcador.lat, marcador.lng)
                            icon = crearIconoBotica(
                                context = context,
                                nombre = marcador.producto.boticaNombre,
                                precio = "S/ ${String.format(Locale.US, "%.2f", marcador.producto.precio)}",
                                colorFondo = colorFondo,
                                colorTexto = colorTexto
                            )
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ ->
                                alTocar(marcador.producto.productoId)
                                true
                            }
                        }
                    )
                }
                vista.invalidate()
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        )
        if (sinUbicacion > 0) {
            Text(
                if (sinUbicacion == 1) {
                    "1 botica no tiene ubicación registrada y no aparece en el mapa"
                } else {
                    "$sinUbicacion boticas no tienen ubicación registrada y no aparecen en el mapa"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

private fun ajustarVista(mapView: MapView, marcadores: List<MarcadorBotica>) {
    val densidad = mapView.context.resources.displayMetrics.density
    when {
        marcadores.isEmpty() -> {
            mapView.controller.setZoom(12.0)
            mapView.controller.setCenter(CENTRO_LIMA)
        }
        else -> {
            val puntos = marcadores.map { GeoPoint(it.lat, it.lng) }
            val caja = BoundingBox.fromGeoPoints(puntos)
            if (caja.latitudeSpan == 0.0 && caja.longitudeSpan == 0.0) {
                mapView.controller.setZoom(15.0)
                mapView.controller.setCenter(puntos.first())
            } else {
                mapView.zoomToBoundingBox(caja, false, (56 * densidad).toInt())
            }
        }
    }
}

// Dibuja la etiqueta del marcador: nombre de la botica y precio, con una punta hacia el lugar
private fun crearIconoBotica(
    context: Context,
    nombre: String,
    precio: String,
    colorFondo: Int,
    colorTexto: Int
): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val pNombre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTexto
        textSize = 12f * d
        typeface = Typeface.DEFAULT_BOLD
    }
    val pPrecio = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTexto
        textSize = 13f * d
        typeface = Typeface.DEFAULT_BOLD
    }
    val nombreCorto = if (nombre.length > 22) nombre.take(21) + "…" else nombre
    val ancho = (maxOf(pNombre.measureText(nombreCorto), pPrecio.measureText(precio)) + 20f * d).toInt()
    val altoCaja = (46f * d).toInt()
    val altoPunta = (8f * d).toInt()

    val bitmap = Bitmap.createBitmap(ancho, altoCaja + altoPunta, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pFondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFondo }

    canvas.drawRoundRect(RectF(0f, 0f, ancho.toFloat(), altoCaja.toFloat()), 10f * d, 10f * d, pFondo)
    val punta = Path().apply {
        moveTo(ancho / 2f - 8f * d, altoCaja.toFloat())
        lineTo(ancho / 2f + 8f * d, altoCaja.toFloat())
        lineTo(ancho / 2f, (altoCaja + altoPunta).toFloat())
        close()
    }
    canvas.drawPath(punta, pFondo)
    canvas.drawText(nombreCorto, 10f * d, 18f * d, pNombre)
    canvas.drawText(precio, 10f * d, 36f * d, pPrecio)
    return BitmapDrawable(context.resources, bitmap)
}