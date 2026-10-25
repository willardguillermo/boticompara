package com.boticompara.app.vistas

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.remote.FavoritoResponse
import java.util.Locale

// ===================== MIS FAVORITOS (H44, H45) =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritosScreen(
    navController: NavController,
    favoritosViewModel: FavoritosViewModel = viewModel()
) {
    val vm = favoritosViewModel
    val snackbarHostState = remember { SnackbarHostState() }

    // Si el token venció, volver al login limpiando la navegación
    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Cada vez que la pantalla vuelve a estar visible se actualiza la lista
    LifecycleResumeEffect(Unit) {
        vm.cargar()
        onPauseOrDispose { }
    }

    // Aviso al quitar un favorito
    LaunchedEffect(vm.mensajeAccion) {
        vm.mensajeAccion?.let {
            snackbarHostState.showSnackbar(it)
            vm.mensajeMostrado()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Mis favoritos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (vm.favoritos.isNotEmpty()) {
                            Text(
                                if (vm.favoritos.size == 1) "1 producto guardado"
                                else "${vm.favoritos.size} productos guardados",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            when {
                vm.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Cargando tus favoritos…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                vm.favoritos.isEmpty() && vm.mensajeError != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EstadoVacio(Icons.Rounded.Warning, vm.mensajeError!!)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { vm.cargar() }) {
                            Text("Reintentar")
                        }
                    }
                }

                vm.favoritos.isEmpty() -> {
                    EstadoVacio(
                        Icons.Rounded.FavoriteBorder,
                        "Todavía no tienes favoritos. Guarda un producto desde su detalle para verlo aquí."
                    )
                }

                else -> {
                    PullToRefreshBox(
                        isRefreshing = vm.isRefreshing,
                        onRefresh = { vm.refrescar() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                        ) {
                            items(vm.favoritos, key = { it.productoId }) { favorito ->
                                FavoritoCard(
                                    favorito = favorito,
                                    onClick = { navController.navigate("detalle/${favorito.productoId}") },
                                    onQuitar = { vm.quitar(favorito.productoId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== TARJETA DE FAVORITO (H44, H45) =====================
@Composable
private fun FavoritoCard(
    favorito: FavoritoResponse,
    onClick: () -> Unit,
    onQuitar: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nombre + botón para quitar de favoritos (H45)
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        favorito.nombreComercial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        favorito.presentacion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onQuitar) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "Quitar de favoritos",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            Etiqueta(texto = favorito.principioActivo, icono = Icons.Rounded.Medication)
            Spacer(Modifier.height(12.dp))

            // Precio + stock
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", favorito.precio)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                EtiquetaStock(favorito.stock)
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
                        favorito.boticaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LineaConIcono(
                        icono = Icons.Rounded.LocationOn,
                        texto = "${favorito.boticaDireccion}, ${favorito.boticaDistrito}"
                    )
                    LineaConIcono(
                        icono = Icons.Rounded.Event,
                        texto = "Guardado el ${formatearFecha(favorito.fecha)}",
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// Convierte la fecha ISO del backend (UTC) a dd/MM/yyyy en la zona horaria del celular
private fun formatearFecha(iso: String): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val fecha = java.time.Instant.parse(iso)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            return fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            // si no se puede interpretar, se usa el formato simple de abajo
        }
    }
    val partes = iso.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else iso
}