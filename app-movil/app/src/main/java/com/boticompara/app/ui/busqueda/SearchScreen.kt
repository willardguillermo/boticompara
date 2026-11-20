package com.boticompara.app.ui.busqueda

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.remote.dto.ProductoResponse
import com.boticompara.app.ui.components.*
import com.boticompara.app.ui.theme.Dimens
import java.util.Locale

private fun etiquetaRadio(radioKm: Double?): String =
    if (radioKm == null) "Sin límite" else "${radioKm.toInt()} km"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, searchViewModel: SearchViewModel = viewModel()) {
    val vm = searchViewModel

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        if (resultado.values.any { it }) vm.activarCercanas() else vm.permisoDenegado()
    }
    val pedirPermisoUbicacion = {
        permisoLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.recargarAlVolver()
        onPauseOrDispose { }
    }

    if (vm.mostrarSelector) {
        SelectorUbicacionDialog(
            ubicacionActual = vm.ubicacionManual,
            onSeleccionar = { etiqueta, lat, lng -> vm.fijarUbicacionManual(etiqueta, lat, lng) },
            onUsarGps = {
                vm.quitarUbicacionManual()
                if (vm.tienePermisoUbicacion()) vm.activarCercanas() else pedirPermisoUbicacion()
            },
            onCerrar = { vm.cerrarSelector() }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CabeceraPantalla(
                titulo = "Buscar medicamentos",
                saludo = "Hola"
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimens.Space20)
        ) {
            Spacer(Modifier.height(Dimens.Space8))

            // Buscador en píldora de 28dp con sombra
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp
            ) {
                OutlinedTextField(
                    value = vm.searchQuery,
                    onValueChange = { vm.onQueryChange(it) },
                    placeholder = {
                        Text(if (vm.searchType == "nombre") "Ej. Panadol" else "Ej. Paracetamol")
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.limpiar() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Borrar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.buscarProductos() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(Dimens.Space12))

            // Chips en una sola fila con horizontalScroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = vm.searchType == "nombre",
                    onClick = { vm.onTipoChange("nombre") },
                    label = { Text("Por nombre") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.LocalPharmacy,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    shape = MaterialTheme.shapes.extraLarge
                )
                FilterChip(
                    selected = vm.searchType == "principio",
                    onClick = { vm.onTipoChange("principio") },
                    label = { Text("Por principio activo") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    shape = MaterialTheme.shapes.extraLarge
                )
                FilterChip(
                    selected = vm.ordenCercanas,
                    onClick = {
                        when {
                            vm.ordenCercanas -> vm.desactivarCercanas()
                            vm.ubicacionManual != null || vm.tienePermisoUbicacion() -> vm.activarCercanas()
                            else -> pedirPermisoUbicacion()
                        }
                    },
                    label = {
                        Text(if (vm.buscandoUbicacion) "Obteniendo ubicación..." else "Más cercanas")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.NearMe,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    shape = MaterialTheme.shapes.extraLarge
                )
            }

            Spacer(Modifier.height(Dimens.Space12))

            // Tarjeta compacta de ubicación y radio con ícono circular y FilledTonalButton "Cambiar"
            val etiquetaUbicacion = vm.ubicacionManual?.etiqueta
                ?: if (vm.ordenCercanas) "Tu ubicación (GPS)" else "Sin definir"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconoCircular(
                            icono = Icons.Rounded.LocationOn,
                            tamano = 36.dp,
                            fondo = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(Dimens.Space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Ubicación",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                etiquetaUbicacion,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(Dimens.Space8))
                        FilledTonalButton(
                            onClick = { vm.abrirSelector() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text("Cambiar")
                        }
                    }

                    if (vm.mensajeUbicacion != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            vm.mensajeUbicacion!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    AnimatedVisibility(visible = !vm.vistaMapa) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Radio:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                SearchViewModel.OPCIONES_RADIO.forEach { opcion ->
                                    FilterChip(
                                        selected = vm.radioKm == opcion,
                                        onClick = { vm.onRadioChange(opcion) },
                                        enabled = vm.ordenCercanas,
                                        label = { Text(etiquetaRadio(opcion)) },
                                        shape = MaterialTheme.shapes.extraLarge,
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }
                            if (!vm.ordenCercanas) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Activa Más cercanas para elegir el radio",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Dimens.Space12))

            if (vm.isLoading && !vm.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                )
                Spacer(Modifier.height(Dimens.Space8))
                Text(
                    "Buscando en boticas...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (!vm.vistaMapa) {
                Text(
                    if (vm.productos.isNotEmpty()) "Desliza hacia abajo para actualizar los precios"
                    else "Los resultados aparecen mientras escribes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Dimens.Space12))

            when {
                vm.productos.isNotEmpty() -> {
                    val radio = vm.radioKm
                    Text(
                        "${vm.productos.size} resultados · " +
                                when {
                                    vm.ordenCercanas && radio != null ->
                                        "a ${radio.toInt()} km o menos, de la más cercana a la más lejana"
                                    vm.ordenCercanas -> "de la más cercana a la más lejana"
                                    else -> "del más barato al más caro"
                                },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Dimens.Space8))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !vm.vistaMapa,
                            onClick = { vm.cambiarVista(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Lista") }
                        SegmentedButton(
                            selected = vm.vistaMapa,
                            onClick = { vm.cambiarVista(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Mapa") }
                    }
                    Spacer(Modifier.height(Dimens.Space8))

                    AnimatedContent(
                        targetState = vm.vistaMapa,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        transitionSpec = { fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200)) }
                    ) { esMapa ->
                        if (esMapa) {
                            MapaResultados(
                                productos = vm.productos,
                                onProductoClick = { productoId -> navController.navigate("detalle/$productoId") },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val precioMinimo = vm.productos.minOf { it.precio }

                            PullToRefreshBox(
                                isRefreshing = vm.isRefreshing,
                                onRefresh = { vm.refrescar() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
                                    contentPadding = PaddingValues(bottom = Dimens.Space24)
                                ) {
                                    itemsIndexed(vm.productos, key = { _, p -> p.productoId }) { indice, producto ->
                                        ProductoCard(
                                            nombre = producto.nombreComercial,
                                            presentacion = producto.presentacion,
                                            principioActivo = producto.principioActivo,
                                            precio = producto.precio,
                                            stock = producto.stock,
                                            boticaNombre = producto.boticaNombre,
                                            boticaDireccion = producto.boticaDireccion,
                                            boticaDistrito = producto.boticaDistrito,
                                            distanciaKm = producto.distanciaKm,
                                            esMasBarato = if (vm.ordenCercanas) producto.precio == precioMinimo else indice == 0,
                                            onClick = { navController.navigate("detalle/${producto.productoId}") },
                                            condicionVenta = producto.condicionVenta
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                vm.isLoading ->
                    EstadoCargando("Comparando precios...")

                vm.mensaje != null -> EstadoVacio(Icons.Rounded.SearchOff, vm.mensaje!!)

                else -> EstadoVacio(
                    Icons.Rounded.Medication,
                    "Escribe el nombre de un medicamento para comparar precios entre boticas"
                )
            }
        }
    }
}
