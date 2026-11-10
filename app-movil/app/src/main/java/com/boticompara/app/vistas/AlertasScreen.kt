package com.boticompara.app.vistas

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.boticompara.app.data.remote.AlertaResponse
import java.util.Locale

// ===================== ALERTAS DE PRECIO (H59) =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertasScreen(
    navController: NavController,
    alertasViewModel: AlertasViewModel
) {
    val vm = alertasViewModel
    val snackbarHostState = remember { SnackbarHostState() }

    // Si el token venció, volver al login limpiando la navegación
    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            vm.sesionVencidaAtendida()
        }
    }

    // Cada vez que la pantalla vuelve a estar visible se actualiza la lista
    LifecycleResumeEffect(Unit) {
        vm.cargar()
        onPauseOrDispose { }
    }

    // Aviso al marcar una alerta como leída
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
                            "Alertas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (vm.alertas.isNotEmpty()) {
                            Text(
                                if (vm.alertas.size == 1) "1 alerta sin leer"
                                else "${vm.alertas.size} alertas sin leer",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (vm.alertas.isNotEmpty()) {
                        TextButton(onClick = { vm.marcarTodasLeidas() }) {
                            Text("Marcar todas")
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
                            "Cargando tus alertas…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                vm.alertas.isEmpty() && vm.mensajeError != null -> {
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

                vm.alertas.isEmpty() -> {
                    EstadoVacio(
                        Icons.Rounded.NotificationsNone,
                        "No tienes alertas nuevas. Te avisaremos cuando baje el precio de uno de tus favoritos."
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
                            items(vm.alertas, key = { it.id }) { alerta ->
                                AlertaCard(
                                    alerta = alerta,
                                    onClick = { navController.navigate("detalle/${alerta.productoId}") },
                                    onMarcarLeida = { vm.marcarLeida(alerta.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== TARJETA DE ALERTA (H59) =====================
@Composable
private fun AlertaCard(
    alerta: AlertaResponse,
    onClick: () -> Unit,
    onMarcarLeida: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nombre + cuánto bajó
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        alerta.nombreComercial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!alerta.presentacion.isNullOrBlank()) {
                        Text(
                            alerta.presentacion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Etiqueta(
                    texto = "Ahorras S/ ${String.format(Locale.US, "%.2f", alerta.ahorro)}",
                    icono = Icons.Rounded.Savings,
                    fondo = MaterialTheme.colorScheme.primary,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.height(12.dp))

            // Precio anterior -> precio nuevo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", alerta.precioAnterior)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "bajó a",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", alerta.precioNuevo)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))

            // Botica y fecha
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoCircular(icono = Icons.Rounded.LocalPharmacy, tamano = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        alerta.boticaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LineaConIcono(
                        icono = Icons.Rounded.Event,
                        texto = formatearFechaAlerta(alerta.fecha),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onMarcarLeida,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Marcar como leída")
            }
        }
    }
}

// Convierte la fecha ISO del backend (UTC) a dd/MM/yyyy en la zona horaria del celular
private fun formatearFechaAlerta(iso: String): String {
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