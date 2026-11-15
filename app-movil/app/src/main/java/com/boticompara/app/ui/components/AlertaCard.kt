package com.boticompara.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.theme.BotiComparaAppTheme
import com.boticompara.app.ui.theme.Dimens
import com.boticompara.app.ui.theme.LocalExtendedColors
import java.util.Locale

@Composable
fun AlertaCard(
    nombreComercial: String,
    presentacion: String?,
    boticaNombre: String,
    boticaDireccion: String? = null,
    boticaDistrito: String? = null,
    precioAnterior: Double,
    precioNuevo: Double,
    ahorro: Double,
    fecha: String,
    leida: Boolean,
    onClick: () -> Unit,
    onMarcarLeida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current
    val containerColor = if (leida) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    }
    val border = if (!leida) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (leida) 1.dp else 4.dp),
        border = border
    ) {
        Column(modifier = Modifier.padding(Dimens.Space16)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!leida) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            nombreComercial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!presentacion.isNullOrBlank()) {
                        Text(
                            presentacion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(Dimens.Space8))
                Etiqueta(
                    texto = "Ahorras S/ ${String.format(Locale.US, "%.2f", ahorro)}",
                    icono = Icons.Rounded.Savings,
                    fondo = extendedColors.ahorroContainer,
                    color = extendedColors.onAhorroContainer
                )
            }
            Spacer(Modifier.height(Dimens.Space12))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", precioAnterior)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )
                Spacer(Modifier.width(Dimens.Space8))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Dimens.Space8))
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", precioNuevo)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    fecha,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Dimens.Space12))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Dimens.Space12))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconoCircular(icono = Icons.Rounded.LocalPharmacy, tamano = 40.dp)
                Spacer(Modifier.width(Dimens.Space12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        boticaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val direccionTexto = listOfNotNull(boticaDireccion, boticaDistrito).filter { it.isNotBlank() }.joinToString(", ")
                    if (direccionTexto.isNotBlank()) {
                        LineaConIcono(
                            icono = Icons.Rounded.LocationOn,
                            texto = direccionTexto
                        )
                    }
                    LineaConIcono(
                        icono = Icons.Rounded.Event,
                        texto = fecha,
                        modifier = Modifier.padding(top = Dimens.Space2)
                    )
                }
                if (!leida) {
                    TextButton(onClick = onMarcarLeida) {
                        Text("Marcar leída")
                    }
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AlertaCardPreview() {
    BotiComparaAppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertaCard(
                    nombreComercial = "Ibuprofeno 400mg",
                    presentacion = "Caja x 50 tab",
                    boticaNombre = "InkaFarma",
                    boticaDireccion = "Av. Larco 456",
                    boticaDistrito = "Miraflores",
                    precioAnterior = 20.00,
                    precioNuevo = 15.00,
                    ahorro = 5.00,
                    fecha = "Hoy",
                    leida = false,
                    onClick = {},
                    onMarcarLeida = {}
                )
            }
        }
    }
}
