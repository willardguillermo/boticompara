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
import androidx.compose.ui.text.style.TextAlign
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
            CabeceraPantalla(
                titulo = "Alertas",
                subtitulo = if (vm.alertas.isNotEmpty()) {
                    if (vm.alertas.size == 1) "1 alerta sin leer"
                    else "${vm.alertas.size} alertas sin leer"
                } else null,
                actions = {
                    if (vm.alertas.isNotEmpty()) {
                        TextButton(onClick = { vm.marcarTodasLeidas() }) {
                            Text("Marcar todas")
                        }
                    }
                }
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EstadoCargando("Cargando tus alertas...")
                    }
                }

                vm.alertas.isEmpty() && vm.mensajeError != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EstadoError(vm.mensajeError!!) { vm.cargar() }
                    }
                }

                vm.alertas.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Dimens.Space24),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconoCircular(
                            icono = Icons.Rounded.NotificationsNone,
                            tamano = 96.dp,
                            fondo = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(Dimens.Space16))
                        Text(
                            "Sin alertas nuevas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Dimens.Space8))
                        Text(
                            "Te avisaremos cuando baje el precio de uno de tus favoritos.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Dimens.Space24))
                        FilledTonalButton(
                            onClick = { navController.navigate("favoritos") },
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text("Ir a mis favoritos")
                        }
                    }
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
                                ItemAlertaCard(
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
private fun ItemAlertaCard(
    alerta: AlertaResponse,
    onClick: () -> Unit,
    onMarcarLeida: () -> Unit
) {
    AlertaCard(
        nombreComercial = alerta.nombreComercial,
        presentacion = alerta.presentacion,
        boticaNombre = alerta.boticaNombre,
        precioAnterior = alerta.precioAnterior,
        precioNuevo = alerta.precioNuevo,
        ahorro = alerta.ahorro,
        fecha = formatearFechaAlerta(alerta.fecha),
        leida = alerta.leida,
        onClick = onClick,
        onMarcarLeida = onMarcarLeida
    )
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
