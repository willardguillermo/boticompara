package com.boticompara.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.theme.BotiComparaAppTheme
import com.boticompara.app.ui.theme.Dimens
import java.util.Locale

@Composable
fun ProductoCard(
    nombre: String,
    presentacion: String,
    principioActivo: String,
    precio: Double,
    stock: Int,
    boticaNombre: String,
    boticaDireccion: String,
    boticaDistrito: String,
    distanciaKm: Double?,
    esMasBarato: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    contentAdicional: (@Composable () -> Unit)? = null,
    condicionVenta: String? = null   // H72: etiqueta de receta; sin etiqueta si es null
) {
    val border = if (esMasBarato) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (esMasBarato) 4.dp else 2.dp),
        border = border
    ) {
        Column(modifier = Modifier.padding(Dimens.Space16)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        presentacion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (trailingContent != null) {
                    Spacer(Modifier.width(Dimens.Space8))
                    trailingContent()
                } else if (esMasBarato) {
                    Spacer(Modifier.width(Dimens.Space8))
                    Etiqueta(
                        texto = "Más barato",
                        icono = Icons.Rounded.Savings,
                        fondo = MaterialTheme.colorScheme.primary,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Space8))

            Etiqueta(texto = principioActivo, icono = Icons.Rounded.Medication)
            if (ReglasCondicionVenta.etiqueta(condicionVenta) != null) {
                Spacer(Modifier.height(Dimens.Space8))
                EtiquetaCondicionVenta(condicionVenta)
            }
            Spacer(Modifier.height(Dimens.Space12))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", precio)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                EtiquetaStock(stock)
            }

            if (contentAdicional != null) {
                Spacer(Modifier.height(Dimens.Space12))
                contentAdicional()
            }

            Spacer(Modifier.height(Dimens.Space12))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Dimens.Space12))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoCircular(icono = Icons.Rounded.LocalPharmacy, tamano = 40.dp)
                Spacer(Modifier.width(Dimens.Space12))
                Column {
                    Text(
                        boticaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LineaConIcono(
                        icono = Icons.Rounded.LocationOn,
                        texto = "$boticaDireccion, $boticaDistrito"
                    )
                    if (distanciaKm != null) {
                        LineaConIcono(
                            icono = Icons.Rounded.NearMe,
                            texto = "A ${String.format(Locale.US, "%.1f", distanciaKm)} km de ti",
                            color = MaterialTheme.colorScheme.primary,
                            estilo = MaterialTheme.typography.labelMedium,
                            peso = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = Dimens.Space2)
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ProductoCardPreview() {
    BotiComparaAppTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                ProductoCard(
                    nombre = "Paracetamol 500mg",
                    presentacion = "Caja x 100 tabletas",
                    principioActivo = "Paracetamol",
                    precio = 12.50,
                    stock = 25,
                    boticaNombre = "Boticas Perú",
                    boticaDireccion = "Av. Brasil 1234",
                    boticaDistrito = "Jesús María",
                    distanciaKm = 1.2,
                    esMasBarato = true,
                    onClick = {}
                )
            }
        }
    }
}
