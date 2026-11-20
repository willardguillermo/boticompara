package com.boticompara.app.ui.reservas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.LineaConIcono
import com.boticompara.app.ui.theme.Dimens

// H72, H74: hoja inferior para elegir la cantidad (1 a 10) y confirmar la reserva
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaReserva(
    nombreProducto: String,
    presentacion: String,
    boticaNombre: String,
    precioUnitario: Double,
    stock: Int,
    cantidad: Int,
    enviando: Boolean,
    mensajeError: String?,
    onDisminuir: () -> Unit,
    onAumentar: () -> Unit,
    onConfirmar: () -> Unit,
    onDismiss: () -> Unit
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val total = CantidadReserva.total(precioUnitario, cantidad)

    ModalBottomSheet(
        onDismissRequest = { if (!enviando) onDismiss() },
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Space24)
                .padding(bottom = Dimens.Space24)
                .navigationBarsPadding()
        ) {
            Text("Reservar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Dimens.Space4))
            Text(
                "$nombreProducto · $presentacion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "en $boticaNombre",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Dimens.Space24))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cantidad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Máximo ${CantidadReserva.maximaPara(stock)} unidades",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = onDisminuir,
                    enabled = !enviando && CantidadReserva.puedeDisminuir(cantidad),
                    modifier = Modifier.size(Dimens.MinTouchTarget)
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Quitar una unidad")
                }
                Text(
                    cantidad.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = 56.dp)
                        .semantics {
                            contentDescription = "Cantidad: $cantidad"
                            liveRegion = LiveRegionMode.Polite
                        }
                )
                FilledTonalIconButton(
                    onClick = onAumentar,
                    enabled = !enviando && CantidadReserva.puedeAumentar(cantidad, stock),
                    modifier = Modifier.size(Dimens.MinTouchTarget)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Agregar una unidad")
                }
            }

            Spacer(Modifier.height(Dimens.Space20))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Dimens.Space16))

            FilaMonto("Precio unitario", formatearSoles(precioUnitario))
            Spacer(Modifier.height(Dimens.Space8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    formatearSoles(total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(Dimens.Space16))
            LineaConIcono(
                icono = Icons.Rounded.Info,
                texto = "Apartas las unidades por 24 horas. Sigue el estado en Mis reservas.",
                estilo = MaterialTheme.typography.bodySmall
            )

            if (mensajeError != null) {
                Spacer(Modifier.height(Dimens.Space12))
                LineaConIcono(
                    icono = Icons.Rounded.Warning,
                    texto = mensajeError,
                    color = MaterialTheme.colorScheme.error,
                    estilo = MaterialTheme.typography.bodyMedium,
                    peso = FontWeight.SemiBold,
                    maxLineas = 3,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            Spacer(Modifier.height(Dimens.Space20))
            if (enviando) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimens.IconMedium), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Dimens.Space12))
                    Text("Reservando...", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                BotiButton(
                    text = "Confirmar reserva",
                    onClick = onConfirmar,
                    icon = Icons.Rounded.EventAvailable,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FilaMonto(etiqueta: String, valor: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
