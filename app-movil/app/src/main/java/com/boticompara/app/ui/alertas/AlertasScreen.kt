package com.boticompara.app.ui.alertas

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
import com.boticompara.app.data.remote.dto.AlertaResponse
import com.boticompara.app.ui.components.*
import com.boticompara.app.ui.theme.Dimens
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertasScreen(
    navController: NavController,
    alertasViewModel: AlertasViewModel
) {
    val vm = alertasViewModel
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            vm.sesionVencidaAtendida()
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.cargar()
        onPauseOrDispose { }
    }

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
                .padding(horizontal = Dimens.Space20)
        ) {
            when {
                vm.isLoading -> {
                    EstadoCargando("Cargando tus alertas...")
                }

                vm.alertas.isEmpty() && vm.mensajeError != null -> {
                    EstadoError(vm.mensajeError!!) { vm.cargar() }
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
                            verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
                            contentPadding = PaddingValues(top = Dimens.Space8, bottom = Dimens.Space24)
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

@Composable
private fun AlertaCard(
    alerta: AlertaResponse,
    onClick: () -> Unit,
    onMarcarLeida: () -> Unit
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
                Spacer(Modifier.width(Dimens.Space8))
                Etiqueta(
                    texto = "Ahorras S/ ${String.format(Locale.US, "%.2f", alerta.ahorro)}",
                    icono = Icons.Rounded.Savings,
                    fondo = MaterialTheme.colorScheme.primary,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.height(Dimens.Space12))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", alerta.precioAnterior)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )
                Spacer(Modifier.width(Dimens.Space8))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "bajó a",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Dimens.Space8))
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", alerta.precioNuevo)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(Dimens.Space12))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoCircular(icono = Icons.Rounded.LocalPharmacy, tamano = 40.dp)
                Spacer(Modifier.width(Dimens.Space12))
                Column {
                    Text(
                        alerta.boticaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LineaConIcono(
                        icono = Icons.Rounded.Event,
                        texto = formatearFechaAlerta(alerta.fecha),
                        modifier = Modifier.padding(top = Dimens.Space2)
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Space12))

            FilledTonalButton(
                onClick = onMarcarLeida,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Dimens.Space8))
                Text("Marcar como leída")
            }
        }
    }
}

private fun formatearFechaAlerta(iso: String): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val fecha = java.time.Instant.parse(iso)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            return fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            // fallback
        }
    }
    val partes = iso.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else iso
}
