package com.boticompara.app.vistas

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.remote.ProductoResponse
import java.util.Locale

// ===================== BÚSQUEDA (H22, H23, H24, H25, H26, H27, H40) =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, searchViewModel: SearchViewModel = viewModel()) {
    val vm = searchViewModel

    // H27: pide el permiso de ubicación y, si lo dan, activa "Más cercanas"
    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        if (resultado.values.any { it }) vm.activarCercanas() else vm.permisoDenegado()
    }

    // Si el token venció, volver al login limpiando la navegación
    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // H40: cada vez que la pantalla vuelve a estar visible (ej. al regresar del perfil) se recarga
    LifecycleResumeEffect(Unit) {
        vm.recargarAlVolver()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ---------- Encabezado ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Hola 👋",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Buscar medicamentos",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FilledTonalButton(
                onClick = { navController.navigate("perfil") },
                shape = RoundedCornerShape(50)
            ) {
                Text("👤 Perfil", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))

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
                    leadingIcon = { Text("🔍", fontSize = 18.sp) },
                    trailingIcon = {
                        if (vm.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.limpiar() }) {
                                Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                // Selector de criterio (H22 / H23)
                Row {
                    FilterChip(
                        selected = vm.searchType == "nombre",
                        onClick = { vm.onTipoChange("nombre") },
                        label = { Text("Por nombre") },
                        shape = RoundedCornerShape(50)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = vm.searchType == "principio",
                        onClick = { vm.onTipoChange("principio") },
                        label = { Text("Por principio activo") },
                        shape = RoundedCornerShape(50)
                    )
                }
                Spacer(Modifier.height(8.dp))

                // H27: ordenar por cercanía
                FilterChip(
                    selected = vm.ordenCercanas,
                    onClick = {
                        when {
                            vm.ordenCercanas -> vm.desactivarCercanas()
                            vm.tienePermisoUbicacion() -> vm.activarCercanas()
                            else -> permisoLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    label = {
                        Text(if (vm.buscandoUbicacion) "Obteniendo ubicación…" else "📍 Más cercanas")
                    },
                    shape = RoundedCornerShape(50)
                )
                if (vm.mensajeUbicacion != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        vm.mensajeUbicacion!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
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
                        if (vm.productos.isNotEmpty()) "↓ Desliza hacia abajo para actualizar los precios"
                        else "✨ Los resultados aparecen mientras escribes",
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
                Text(
                    "${vm.productos.size} resultados · " +
                            if (vm.ordenCercanas) "de la más cercana a la más lejana" else "del más barato al más caro",
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
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
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

            vm.mensaje != null -> EstadoVacio("🔎", vm.mensaje!!)

            else -> EstadoVacio("💊", "Escribe el nombre de un medicamento para comparar precios entre boticas")
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
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "💰 Más barato",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Principio activo
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    "💊 ${producto.principioActivo}",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
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
                val pocoStock = producto.stock <= 10
                Surface(
                    color = if (pocoStock) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        if (pocoStock) "⚠️ Quedan ${producto.stock}" else "✅ Stock: ${producto.stock}",
                        color = if (pocoStock) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Botica
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏪", fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        producto.boticaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "📍 ${producto.boticaDireccion}, ${producto.boticaDistrito}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // H27: distancia desde la ubicación del comprador
                    if (producto.distanciaKm != null) {
                        Text(
                            "🚶 a ${String.format(Locale.US, "%.1f", producto.distanciaKm)} km de ti",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ===================== ESTADO VACÍO =====================
// Para "sin resultados", errores o el inicio de la búsqueda
@Composable
fun EstadoVacio(emoji: String, texto: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 36.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}