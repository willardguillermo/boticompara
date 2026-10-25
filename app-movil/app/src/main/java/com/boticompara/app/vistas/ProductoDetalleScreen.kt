package com.boticompara.app.vistas

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.remote.AlternativaResponse
import java.util.Locale

// ===================== PANTALLA DETALLE DEL PRODUCTO (H28, H36, H37, H41) =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductoDetalleScreen(
    productoId: Long,
    navController: NavController,
    detalleViewModel: ProductoDetalleViewModel = viewModel()
) {
    val vm = detalleViewModel
    val context = LocalContext.current

    LaunchedEffect(productoId) {
        vm.cargarDetalle(productoId)
    }

    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Detalle del producto", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
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
                            "Cargando detalle…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                vm.mensajeError != null -> {
                    EstadoVacio(Icons.Rounded.Warning, vm.mensajeError!!)
                }

                vm.productoDetalle != null -> {
                    val producto = vm.productoDetalle!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        // Tarjeta principal del producto
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    producto.nombreComercial,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    producto.presentacion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))

                                Etiqueta(texto = producto.principioActivo, icono = Icons.Rounded.Medication)

                                if (!producto.fabricante.isNullOrEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Laboratorio: ${producto.fabricante}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(16.dp))

                                // Precio y stock
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(
                                            "Precio unitario",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "S/ ${String.format(Locale.US, "%.2f", producto.precio)}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    EtiquetaStock(producto.stock)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Tarjeta de la botica
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "Disponible en",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconoCircular(icono = Icons.Rounded.LocalPharmacy, tamano = 48.dp)
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            producto.boticaNombre,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        LineaConIcono(
                                            icono = Icons.Rounded.LocationOn,
                                            texto = "${producto.boticaDireccion}, ${producto.boticaDistrito}",
                                            estilo = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                // Teléfono de la botica: abre el marcador del celular (no pide permisos)
                                if (!producto.boticaTelefono.isNullOrBlank()) {
                                    Spacer(Modifier.height(16.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${producto.boticaTelefono}"))
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Rounded.Call,
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                        Text("Llamar · ${producto.boticaTelefono}")
                                    }
                                }
                            }
                        }

                        // ---------- Alternativas (H36, H37, H41) ----------
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconoCircular(icono = Icons.Rounded.Savings, tamano = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Alternativas",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Otras opciones para el mismo medicamento",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        val datosAlternativas = vm.alternativas
                        when {
                            vm.cargandoAlternativas -> {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Buscando alternativas…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            vm.errorAlternativas || datosAlternativas == null -> {
                                SinAlternativasCard(
                                    icono = Icons.Rounded.Warning,
                                    titulo = "No pudimos cargar las alternativas",
                                    detalle = "Revisa tu conexión e inténtalo de nuevo más tarde."
                                )
                            }

                            // H41: no hay genéricos ni equivalentes
                            datosAlternativas.sinEquivalentes || datosAlternativas.alternativas.isEmpty() -> {
                                SinAlternativasCard(
                                    icono = Icons.Rounded.SearchOff,
                                    titulo = "No hay genéricos registrados",
                                    detalle = "Por ahora no encontramos otra opción para este medicamento."
                                )
                            }

                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    datosAlternativas.alternativas.forEach { alternativa ->
                                        AlternativaCard(
                                            alternativa = alternativa,
                                            onClick = {
                                                navController.navigate("detalle/${alternativa.productoId}")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== TARJETA DE ALTERNATIVA (H36, H37) =====================
@Composable
private fun AlternativaCard(alternativa: AlternativaResponse, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nombre + etiqueta de ahorro (H37)
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        alternativa.nombreComercial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        alternativa.presentacion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // H37: solo aparece si la alternativa es más barata que el producto consultado
                if (alternativa.ahorroPorcentaje != null) {
                    Spacer(Modifier.width(8.dp))
                    Etiqueta(
                        texto = "Ahorras ${String.format(Locale.US, "%.1f", alternativa.ahorroPorcentaje)} %",
                        icono = Icons.Rounded.Savings,
                        fondo = MaterialTheme.colorScheme.primary,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Tipo de coincidencia (H36)
            if (alternativa.tipoCoincidencia == "EQUIVALENTE") {
                Etiqueta(texto = "Genérico equivalente", icono = Icons.Rounded.CheckCircle)
            } else {
                Etiqueta(texto = "Mismo principio activo", icono = Icons.Rounded.Medication)
            }
            Spacer(Modifier.height(12.dp))

            // Precio + stock
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", alternativa.precio)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                EtiquetaStock(alternativa.stock)
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
                        alternativa.boticaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LineaConIcono(
                        icono = Icons.Rounded.LocationOn,
                        texto = "${alternativa.boticaDireccion}, ${alternativa.boticaDistrito}"
                    )
                }
            }
        }
    }
}

// ===================== SIN ALTERNATIVAS / ERROR (H41) =====================
@Composable
private fun SinAlternativasCard(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    detalle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconoCircular(icono = icono, tamano = 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                detalle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}