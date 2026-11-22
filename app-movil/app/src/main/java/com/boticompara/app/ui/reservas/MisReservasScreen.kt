package com.boticompara.app.ui.reservas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.remote.dto.ReservaResponse
import com.boticompara.app.ui.components.*
import com.boticompara.app.ui.theme.Dimens

// H76: historial de reservas del comprador, con deslizar para actualizar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisReservasScreen(
    navController: NavController,
    misReservasViewModel: MisReservasViewModel = viewModel()
) {
    val vm = misReservasViewModel

    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Se recarga al volver del detalle, por si se canceló una reserva
    LifecycleResumeEffect(Unit) {
        vm.cargar()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis reservas", fontWeight = FontWeight.Bold)
                        if (vm.reservas.isNotEmpty()) {
                            Text(
                                if (vm.reservas.size == 1) "1 reserva" else "${vm.reservas.size} reservas",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = vm.isRefreshing,
            onRefresh = { vm.refrescar() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                vm.isLoading -> EstadoCargando("Cargando tus reservas...")

                vm.reservas.isEmpty() -> {
                    // Con scroll para que también se pueda deslizar y actualizar
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Dimens.Space20)
                    ) {
                        if (vm.mensajeError != null) {
                            EstadoError(vm.mensajeError!!) { vm.cargar() }
                        } else {
                            EstadoVacio(
                                Icons.AutoMirrored.Rounded.ReceiptLong,
                                "Todavía no tienes reservas. Reserva un producto desde su detalle para recogerlo en la botica."
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
                        contentPadding = PaddingValues(
                            start = Dimens.Space20,
                            end = Dimens.Space20,
                            top = Dimens.Space8,
                            bottom = Dimens.Space24
                        )
                    ) {
                        items(vm.reservas, key = { it.id }) { reserva ->
                            ReservaCard(
                                reserva = reserva,
                                onClick = { navController.navigate("reserva/${reserva.id}?nueva=false") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservaCard(reserva: ReservaResponse, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(Dimens.Space16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reserva.codigo,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Dimens.Space8))
                EstadoReservaChip(reserva.estado)
            }
            Spacer(Modifier.height(Dimens.Space8))
            Text(
                reserva.nombreComercial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                reserva.presentacion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.Space8))
            LineaConIcono(
                icono = Icons.Rounded.LocalPharmacy,
                texto = reserva.boticaNombre,
                estilo = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(Dimens.Space12))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Dimens.Space12))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${reserva.cantidad} × ${formatearSoles(reserva.precioUnitario)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatearSoles(reserva.total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(Dimens.Space4))
            LineaConIcono(
                icono = Icons.Rounded.Event,
                texto = FechaLima.formatear(reserva.creadoEn),
                estilo = MaterialTheme.typography.bodySmall
            )
        }
    }
}
