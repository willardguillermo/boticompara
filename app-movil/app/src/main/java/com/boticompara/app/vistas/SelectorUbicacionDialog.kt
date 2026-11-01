package com.boticompara.app.vistas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.boticompara.app.data.local.Distrito
import com.boticompara.app.data.local.Distritos
import com.boticompara.app.data.local.UbicacionManual
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

private val CENTRO_LIMA = GeoPoint(-12.0464, -77.0300)
private const val ETIQUETA_PUNTO = "Punto en el mapa"

// H50: pantalla para elegir la ubicación a mano (distrito de la lista o punto en el mapa)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorUbicacionDialog(
    ubicacionActual: UbicacionManual?,
    onSeleccionar: (etiqueta: String, lat: Double, lng: Double) -> Unit,
    onUsarGps: () -> Unit,
    onCerrar: () -> Unit
) {
    var pestana by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Elegir ubicación") },
                    navigationIcon = {
                        IconButton(onClick = onCerrar) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                        }
                    }
                )

                if (ubicacionActual != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Actual: ${ubicacionActual.etiqueta}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onUsarGps) { Text("Usar mi GPS") }
                    }
                }

                PrimaryTabRow(selectedTabIndex = pestana) {
                    Tab(
                        selected = pestana == 0,
                        onClick = { pestana = 0 },
                        text = { Text("Distrito") }
                    )
                    Tab(
                        selected = pestana == 1,
                        onClick = { pestana = 1 },
                        text = { Text("Punto en el mapa") }
                    )
                }

                when (pestana) {
                    0 -> ListaDistritos(
                        seleccionado = ubicacionActual?.etiqueta,
                        onElegir = { onSeleccionar(it.nombre, it.lat, it.lng) }
                    )
                    else -> SelectorMapa(
                        inicial = ubicacionActual,
                        onConfirmar = { lat, lng -> onSeleccionar(ETIQUETA_PUNTO, lat, lng) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListaDistritos(
    seleccionado: String?,
    onElegir: (Distrito) -> Unit
) {
    var filtro by remember { mutableStateOf("") }
    val lista = remember(filtro) { Distritos.filtrar(filtro) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filtro,
            onValueChange = { filtro = it },
            label = { Text("Buscar distrito") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        if (lista.isEmpty()) {
            Text(
                "No hay distritos con ese nombre",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lista, key = { it.nombre }) { distrito ->
                ListItem(
                    headlineContent = { Text(distrito.nombre) },
                    leadingContent = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                    trailingContent = {
                        if (distrito.nombre == seleccionado) {
                            Icon(Icons.Rounded.Check, contentDescription = "Seleccionado")
                        }
                    },
                    modifier = Modifier.clickable { onElegir(distrito) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SelectorMapa(
    inicial: UbicacionManual?,
    onConfirmar: (lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current
    var punto by remember { mutableStateOf(inicial?.let { GeoPoint(it.lat, it.lng) }) }

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
            controller.setZoom(if (inicial != null) 14.0 else 13.0)
            controller.setCenter(inicial?.let { GeoPoint(it.lat, it.lng) } ?: CENTRO_LIMA)
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        punto = GeoPoint(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean = false
                })
            )
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            update = { vista ->
                vista.overlays.removeAll { it is Marker }
                punto?.let { p ->
                    vista.overlays.add(
                        Marker(vista).apply {
                            position = p
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ -> true }
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
        Button(
            onClick = { punto?.let { onConfirmar(it.latitude, it.longitude) } },
            enabled = punto != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(if (punto == null) "Toca el mapa para marcar tu ubicación" else "Usar este punto")
        }
    }
}