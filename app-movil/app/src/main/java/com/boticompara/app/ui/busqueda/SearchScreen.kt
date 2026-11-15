package com.boticompara.app.ui.busqueda

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Hola",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Buscar medicamentos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(Dimens.Space16)) {
                    OutlinedTextField(
                        value = vm.searchQuery,
                        onValueChange = { vm.onQueryChange(it) },
                        label = {
                            Text(if (vm.searchType == "nombre") "Nombre comercial" else "Principio activo")
                        },
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
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.buscarProductos() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Dimens.Space12))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Space4)
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

                    Spacer(Modifier.height(Dimens.Space8))
                    val etiquetaUbicacion = vm.ubicacionManual?.etiqueta
                        ?: if (vm.ordenCercanas) "Tu ubicación (GPS)" else "Sin definir"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Ubicación: $etiquetaUbicacion",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.abrirSelector() }) { Text("Cambiar") }
                    }
                    if (vm.mensajeUbicacion != null) {
                        Text(
                            vm.mensajeUbicacion!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!vm.vistaMapa) {
                        Spacer(Modifier.height(Dimens.Space12))
                        Text(
                            "Radio de búsqueda",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(Dimens.Space4))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                            verticalArrangement = Arrangement.spacedBy(Dimens.Space4)
                        ) {
                            SearchViewModel.OPCIONES_RADIO.forEach { opcion ->
                                FilterChip(
                                    selected = vm.radioKm == opcion,
                                    onClick = { vm.onRadioChange(opcion) },
                                    enabled = vm.ordenCercanas,
                                    label = { Text(etiquetaRadio(opcion)) },
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                            }
                        }
                        if (!vm.ordenCercanas) {
                            Text(
                                "Activa Más cercanas para elegir el radio",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                }
            }
            Spacer(Modifier.height(Dimens.Space16))

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

                    if (vm.vistaMapa) {
                        MapaResultados(
                            productos = vm.productos,
                            onProductoClick = { productoId -> navController.navigate("detalle/$productoId") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
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
                                    ResultadoCard(
                                        producto = producto,
                                        esMasBarato = if (vm.ordenCercanas) producto.precio == precioMinimo else indice == 0,
                                        onClick = { navController.navigate("detalle/${producto.productoId}") }
                                    )
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

@Composable
fun ResultadoCard(
    producto: ProductoResponse,
    esMasBarato: Boolean,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(Dimens.Space16)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        producto.nombreComercial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        producto.presentacion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (esMasBarato) {
                    Spacer(Modifier.width(Dimens.Space8))
                    Etiqueta(
                        texto = "Más barato",
                        icono = Icons.Rounded.Savings,
                        fondo = MaterialTheme.colorScheme.primary,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Space8))

            Etiqueta(texto = producto.principioActivo, icono = Icons.Rounded.Medication)
            Spacer(Modifier.height(Dimens.Space12))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", producto.precio)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                EtiquetaStock(producto.stock)
            }
            Spacer(Modifier.height(Dimens.Space12))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Dimens.Space12))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoCircular(icono = Icons.Rounded.LocalPharmacy, tamano = 40.dp)
                Spacer(Modifier.width(Dimens.Space12))
                Column {
                    Text(
                        producto.boticaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LineaConIcono(
                        icono = Icons.Rounded.LocationOn,
                        texto = "${producto.boticaDireccion}, ${producto.boticaDistrito}"
                    )
                    if (producto.distanciaKm != null) {
                        LineaConIcono(
                            icono = Icons.Rounded.NearMe,
                            texto = "A ${String.format(Locale.US, "%.1f", producto.distanciaKm)} km de ti",
                            color = MaterialTheme.colorScheme.primary,
                            estilo = MaterialTheme.typography.labelMedium,
                            peso = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = Dimens.Space2)
                        )
                    }
                }
            }
        }
    }
}
