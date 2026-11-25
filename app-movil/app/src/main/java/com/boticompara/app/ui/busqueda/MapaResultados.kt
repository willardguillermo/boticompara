package com.boticompara.app.ui.busqueda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.boticompara.app.data.remote.dto.ProductoResponse
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.ProductoCard
import com.boticompara.app.ui.theme.Dimens
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import java.util.Locale

private val CENTRO_LIMA = LatLng(-12.0464, -77.0300)
private const val ZOOM_LIMA = 12f
private const val ZOOM_BOTICA = 15f
private const val MARGEN_MAPA = 96

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

@Composable
fun MapaResultados(
    productos: List<ProductoResponse>,
    onProductoClick: (productoId: Long) -> Unit,
    modifier: Modifier = Modifier,
    ubicacionUsuario: Pair<Double, Double>? = null
) {
    val alTocar by rememberUpdatedState(onProductoClick)

    val marcadores = remember(productos) { agruparPorBotica(productos) }
    val sinUbicacion = remember(productos) {
        productos.map { it.boticaId }.distinct().size - marcadores.size
    }

    var boticaSeleccionada by remember { mutableStateOf<MarcadorBotica?>(null) }
    var tamanoMapa by remember { mutableStateOf(IntSize.Zero) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(CENTRO_LIMA, ZOOM_LIMA)
    }
    val estadoUbicacion = rememberUpdatedMarkerState(
        position = ubicacionUsuario?.let { LatLng(it.first, it.second) } ?: CENTRO_LIMA
    )

    LaunchedEffect(marcadores, ubicacionUsuario, tamanoMapa) {
        if (tamanoMapa.width == 0 || tamanoMapa.height == 0) return@LaunchedEffect
        val puntos = marcadores.map { LatLng(it.lat, it.lng) } +
            (ubicacionUsuario?.let { listOf(LatLng(it.first, it.second)) } ?: emptyList())
        when {
            puntos.isEmpty() ->
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(CENTRO_LIMA, ZOOM_LIMA))

            puntos.size == 1 ->
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(puntos.first(), ZOOM_BOTICA))

            else -> {
                val limites = LatLngBounds.builder()
                puntos.forEach { limites.include(it) }
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngBounds(limites.build(), MARGEN_MAPA)
                )
            }
        }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordenadas ->
                        if (coordenadas.size != tamanoMapa) tamanoMapa = coordenadas.size
                    },
                cameraPositionState = cameraPositionState,
                onMapClick = { boticaSeleccionada = null }
            ) {
                marcadores.forEach { marcador ->
                    Marker(
                        state = MarkerState(position = LatLng(marcador.lat, marcador.lng)),
                        title = marcador.producto.boticaNombre,
                        snippet = "S/ ${String.format(Locale.US, "%.2f", marcador.producto.precio)}",
                        onClick = {
                            boticaSeleccionada = marcador
                            true
                        }
                    )
                }
                if (ubicacionUsuario != null) {
                    Marker(
                        state = estadoUbicacion,
                        title = "Tu ubicación"
                    )
                }
            }
            if (sinUbicacion > 0) {
                Text(
                    if (sinUbicacion == 1) {
                        "1 botica no tiene ubicación registrada y no aparece en el mapa"
                    } else {
                        "$sinUbicacion boticas no tienen ubicación registrada y no aparecen en el mapa"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        boticaSeleccionada?.let { seleccion ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Dimens.Space16)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(Dimens.Space16)) {
                        ProductoCard(
                            nombre = seleccion.producto.nombreComercial,
                            presentacion = seleccion.producto.presentacion,
                            principioActivo = seleccion.producto.principioActivo,
                            precio = seleccion.producto.precio,
                            stock = seleccion.producto.stock,
                            boticaNombre = seleccion.producto.boticaNombre,
                            boticaDireccion = seleccion.producto.boticaDireccion,
                            boticaDistrito = seleccion.producto.boticaDistrito,
                            distanciaKm = seleccion.producto.distanciaKm,
                            esMasBarato = false,
                            onClick = { alTocar(seleccion.producto.productoId) },
                            condicionVenta = seleccion.producto.condicionVenta
                        )
                        Spacer(Modifier.height(Dimens.Space12))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BotiButton(
                                text = "Ver detalle",
                                onClick = { alTocar(seleccion.producto.productoId) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { boticaSeleccionada = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                            }
                        }
                    }
                }
            }
        }
    }
}
