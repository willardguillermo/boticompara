package com.boticompara.app.ui.reservas

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.remote.dto.ReservaResponse
import com.boticompara.app.ui.components.*
import com.boticompara.app.ui.theme.BotiComparaTheme
import com.boticompara.app.ui.theme.Dimens

// H73 (código al crearla), H75 (cancelar) y H76 (detalle de una reserva)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservaDetalleScreen(
    reservaId: Long,
    nueva: Boolean,
    navController: NavController,
    detalleViewModel: ReservaDetalleViewModel = viewModel()
) {
    val vm = detalleViewModel
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmarCancelacion by remember { mutableStateOf(false) }

    LaunchedEffect(reservaId) { vm.cargar(reservaId) }

    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
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
                title = { Text(if (nueva) "Reserva creada" else "Tu reserva", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            val reserva = vm.reserva
            when {
                vm.isLoading -> EstadoCargando("Cargando tu reserva...")

                reserva == null && vm.mensajeError != null ->
                    EstadoError(vm.mensajeError!!) { vm.cargar(reservaId) }

                reserva != null -> ContenidoReserva(
                    reserva = reserva,
                    nueva = nueva,
                    puedeCancelar = vm.puedeCancelar(),
                    cancelando = vm.cancelando,
                    onCancelar = { confirmarCancelacion = true },
                    onIrAMisReservas = {
                        navController.navigate("reservas") {
                            popUpTo(RUTA_RESERVA) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }

    // H75: confirmar antes de cancelar
    if (confirmarCancelacion) {
        AlertDialog(
            onDismissRequest = { confirmarCancelacion = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text("¿Cancelar la reserva?") },
            text = {
                Text(
                    "La botica dejará de apartar ${vm.reserva?.nombreComercial ?: "el producto"}. " +
                            "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmarCancelacion = false
                        vm.cancelar()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Sí, cancelar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarCancelacion = false }) { Text("No, mantener") }
            }
        )
    }
}

const val RUTA_RESERVA = "reserva/{reservaId}?nueva={nueva}"

@Composable
private fun ContenidoReserva(
    reserva: ReservaResponse,
    nueva: Boolean,
    puedeCancelar: Boolean,
    cancelando: Boolean,
    onCancelar: () -> Unit,
    onIrAMisReservas: () -> Unit
) {
    val context = LocalContext.current
    val estado = EstadoReserva.desde(reserva.estado)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = Dimens.Space8, bottom = Dimens.Space24)
    ) {
        TarjetaCodigo(reserva = reserva, nueva = nueva, estado = estado)

        if (estado == EstadoReserva.RECHAZADA) {
            Spacer(Modifier.height(Dimens.Space16))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(Dimens.Space20)) {
                    Text(
                        "Motivo del rechazo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(Dimens.Space4))
                    Text(
                        reserva.motivoRechazo?.takeIf { it.isNotBlank() } ?: "La botica no indicó un motivo.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.Space16))
        TarjetaSeccion {
            SeccionTitulo(titulo = "Producto")
            Spacer(Modifier.height(Dimens.Space8))
            Text(reserva.nombreComercial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                reserva.presentacion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.Space12))
            FilaDato("Cantidad", if (reserva.cantidad == 1) "1 unidad" else "${reserva.cantidad} unidades")
            FilaDato("Precio unitario", formatearSoles(reserva.precioUnitario))
            Spacer(Modifier.height(Dimens.Space8))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Dimens.Space8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    formatearSoles(reserva.total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(Dimens.Space16))
        TarjetaSeccion {
            SeccionTitulo(titulo = "Recoge en")
            Spacer(Modifier.height(Dimens.Space12))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoCircular(icono = Icons.Rounded.LocalPharmacy, tamano = 48.dp)
                Spacer(Modifier.width(Dimens.Space16))
                Column {
                    Text(reserva.boticaNombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LineaConIcono(
                        icono = Icons.Rounded.LocationOn,
                        texto = "${reserva.boticaDireccion}, ${reserva.boticaDistrito}",
                        estilo = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            val telefono = reserva.boticaTelefono
            if (!telefono.isNullOrBlank()) {
                Spacer(Modifier.height(Dimens.Space16))
                BotiTonalButton(
                    text = "Llamar · $telefono",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefono")))
                    },
                    icon = Icons.Rounded.Call,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(Dimens.Space16))
        LineaConIcono(
            icono = Icons.Rounded.Event,
            texto = "Reservada el ${FechaLima.formatear(reserva.creadoEn)}",
            estilo = MaterialTheme.typography.bodyMedium
        )

        if (nueva) {
            Spacer(Modifier.height(Dimens.Space24))
            BotiButton(
                text = "Ir a Mis reservas",
                onClick = onIrAMisReservas,
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // H75: solo PENDIENTE, CONFIRMADA o LISTA, y antes de venceEn
        if (puedeCancelar) {
            Spacer(Modifier.height(if (nueva) Dimens.Space12 else Dimens.Space24))
            OutlinedButton(
                onClick = onCancelar,
                enabled = !cancelando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (cancelando) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimens.IconMedium), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Spacer(Modifier.width(Dimens.Space8))
                    Text("Cancelar reserva", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// H73: el código grande y fácil de leer, para mostrarlo en la botica
@Composable
private fun TarjetaCodigo(reserva: ReservaResponse, nueva: Boolean, estado: EstadoReserva?) {
    val activa = estado?.cancelable == true
    val colores = coloresDe(
        ReglasReserva.tonoTarjetaCodigo(reserva.estado),
        MaterialTheme.colorScheme,
        BotiComparaTheme.extendedColors
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colores.fondo),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Space24),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (nueva) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.IconXLarge)
                )
                Spacer(Modifier.height(Dimens.Space8))
                Text(
                    "¡Listo! Tu reserva fue enviada",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colores.contenido,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Dimens.Space16))
            }
            Text(
                "Código de reserva",
                style = MaterialTheme.typography.labelLarge,
                color = colores.contenido
            )
            Spacer(Modifier.height(Dimens.Space4))
            SelectionContainerCodigo(reserva.codigo, colores.contenido)
            Spacer(Modifier.height(Dimens.Space4))
            Text(
                ReglasReserva.mensajeCodigo(reserva.estado),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colores.contenido,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.Space16))
            EstadoReservaChip(reserva.estado)

            val vence = reserva.venceEn
            if (vence != null && (activa || estado == EstadoReserva.VENCIDA)) {
                Spacer(Modifier.height(Dimens.Space12))
                LineaConIcono(
                    icono = Icons.Rounded.Schedule,
                    texto = if (activa) "Válida hasta el ${FechaLima.formatear(vence)}"
                    else "Venció el ${FechaLima.formatear(vence)}",
                    color = colores.contenido,
                    estilo = MaterialTheme.typography.bodyMedium,
                    peso = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SelectionContainerCodigo(codigo: String, color: Color) {
    // Para TalkBack se lee carácter por carácter: "B C guion 7 K 2 Q X M"
    val lectura = codigo.map { if (it == '-') "guion" else it.toString() }.joinToString(" ")
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            codigo,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            letterSpacing = 2.sp,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Código $lectura" }
        )
    }
}

@Composable
private fun TarjetaSeccion(contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(Dimens.Space20), content = contenido)
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(modifier = Modifier.padding(vertical = Dimens.Space2), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
