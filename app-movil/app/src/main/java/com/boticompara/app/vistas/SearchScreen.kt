package com.boticompara.app.vistas

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
import com.boticompara.app.data.remote.ProductoResponse
import java.util.Locale

// Texto de cada opción del selector de radio (H58)
private fun etiquetaRadio(radioKm: Double?): String =
    if (radioKm == null) "Sin límite" else "${radioKm.toInt()} km"

// ===================== BÚSQUEDA (H22, H23, H24, H25, H26, H27, H40, H50, H58) =====================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(navController: NavController, searchViewModel: SearchViewModel = viewModel()) {
    val vm = searchViewModel

    // H27: pide el permiso de ubicación y, si lo dan, activa "Más cercanas"
    // H50: si lo niegan, se abre la pantalla para elegir la ubicación a mano
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

    // Si el token venció, volver al login limpiando la navegación
    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // H40: cada vez que la pantalla vuelve a estar visible (ej. al regresar del detalle) se recarga
    LifecycleResumeEffect(Unit) {
        vm.recargarAlVolver()
        onPauseOrDispose { }
    }

    // H50: elegir o cambiar la ubicación manualmente
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ---------- Tarjeta de búsqueda ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = vm.searchQuery,
                        onValueChange = { vm.onQueryChange(it) },   // H26: busca sola al dejar de escribir
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
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.buscarProductos() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    // Criterios de búsqueda (H22 / H23) y orden por cercanía (H27)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            shape = RoundedCornerShape(50)
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
                            shape = RoundedCornerShape(50)
                        )
                        FilterChip(
                            selected = vm.ordenCercanas,
                            onClick = {
                                when {
                                    vm.ordenCercanas -> vm.desactivarCercanas()
                                    // H50: con ubicación manual guardada no hace falta el GPS
                                    vm.ubicacionManual != null || vm.tienePermisoUbicacion() -> vm.activarCercanas()
                                    else -> pedirPermisoUbicacion()
                                }
                            },
                            label = {
                                Text(if (vm.buscandoUbicacion) "Obteniendo ubicación…" else "Más cercanas")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.NearMe,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }

                    // ---------- H50: ubicación en uso y botón para cambiarla ----------
                    Spacer(Modifier.height(8.dp))
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

                    // ---------- H58: selector de radio (solo se activa si hay ubicación) ----------
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Radio de búsqueda",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SearchViewModel.OPCIONES_RADIO.forEach { opcion ->
                            FilterChip(
                                selected = vm.radioKm == opcion,
                                onClick = { vm.onRadioChange(opcion) },
                                enabled = vm.ordenCercanas,
                                label = { Text(etiquetaRadio(opcion)) },
                                shape = RoundedCornerShape(50)
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
                    Spacer(Modifier.height(12.dp))

                    // H26: indicador de carga (no se muestra si la carga viene de deslizar, ahí sale el círculo)
                    if (vm.isLoading && !vm.isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Buscando en boticas…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            if (vm.productos.isNotEmpty()) "Desliza hacia abajo para actualizar los precios"
                            else "Los resultados aparecen mientras escribes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---------- Resultados ----------
            when {
                // Hay resultados: se muestran aunque se esté actualizando (no "parpadea")
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
                    Spacer(Modifier.height(8.dp))

                    val precioMinimo = vm.productos.minOf { it.precio }

                    // H40: deslizar hacia abajo para actualizar
                    PullToRefreshBox(
                        isRefreshing = vm.isRefreshing,
                        onRefresh = { vm.refrescar() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(vm.productos, key = { _, p -> p.productoId }) { indice, producto ->
                                ResultadoCard(
                                    producto = producto,
                                    // "Más barato" es el de menor precio, aunque se ordene por cercanía
                                    esMasBarato = if (vm.ordenCercanas) producto.precio == precioMinimo else indice == 0,
                                    onClick = { navController.navigate("detalle/${producto.productoId}") }
                                )
                            }
                        }
                    }
                }

                // Primera búsqueda en curso
                vm.isLoading ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Comparando precios…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                vm.mensaje != null -> EstadoVacio(Icons.Rounded.SearchOff, vm.mensaje!!)

                else -> EstadoVacio(
                    Icons.Rounded.Medication,
                    "Escribe el nombre de un medicamento para comparar precios entre boticas"
                )
            }
        }
    }
}

// ===================== TARJETA DE RESULTADO (H25, H27) =====================
@Composable
fun ResultadoCard(
    producto: ProductoResponse,
    esMasBarato: Boolean,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nombre + etiqueta "Más barato"
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
                    Spacer(Modifier.width(8.dp))
                    Etiqueta(
                        texto = "Más barato",
                        icono = Icons.Rounded.Savings,
                        fondo = MaterialTheme.colorScheme.primary,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Principio activo
            Etiqueta(texto = producto.principioActivo, icono = Icons.Rounded.Medication)
            Spacer(Modifier.height(12.dp))

            // Precio + stock
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
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Botica
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoCircular(icono = Icons.Rounded.LocalPharmacy, tamano = 40.dp)
                Spacer(Modifier.width(12.dp))
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
                    // H27: distancia desde la ubicación del comprador
                    if (producto.distanciaKm != null) {
                        LineaConIcono(
                            icono = Icons.Rounded.NearMe,
                            texto = "A ${String.format(Locale.US, "%.1f", producto.distanciaKm)} km de ti",
                            color = MaterialTheme.colorScheme.primary,
                            estilo = MaterialTheme.typography.labelMedium,
                            peso = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}