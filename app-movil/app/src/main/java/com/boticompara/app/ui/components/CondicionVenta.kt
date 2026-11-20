package com.boticompara.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.theme.BotiComparaAppTheme
import com.boticompara.app.ui.theme.BotiComparaTheme
import com.boticompara.app.ui.theme.Dimens

// H72: condición de venta DIGEMID del medicamento (docs/API.md, sección 5). null = sin información
enum class CondicionVenta {
    SIN_RECETA, CON_RECETA, RECETA_RETENIDA;

    companion object {
        fun desde(valor: String?): CondicionVenta? =
            entries.firstOrNull { it.name.equals(valor?.trim(), ignoreCase = true) }
    }
}

object ReglasCondicionVenta {
    const val AVISO_CON_RECETA = "Presenta tu receta médica al recoger; sin ella la botica no puede entregarlo"
    const val AVISO_RECETA_RETENIDA =
        "Este medicamento se vende con receta retenida: acércate a la botica con tu receta médica"

    // Texto de la etiqueta; null = sin etiqueta (SIN_RECETA o sin información)
    fun etiqueta(condicion: String?): String? = when (CondicionVenta.desde(condicion)) {
        CondicionVenta.CON_RECETA -> "Requiere receta médica"
        CondicionVenta.RECETA_RETENIDA -> "Receta retenida"
        CondicionVenta.SIN_RECETA, null -> null
    }

    // H72: se reserva si hay stock y no es de receta retenida (el servidor respondería 409)
    fun sePuedeReservar(stock: Int, condicion: String?): Boolean =
        stock > 0 && CondicionVenta.desde(condicion) != CondicionVenta.RECETA_RETENIDA

    fun esRecetaRetenida(condicion: String?): Boolean =
        CondicionVenta.desde(condicion) == CondicionVenta.RECETA_RETENIDA

    // Aviso de la receta al reservar y en la reserva: solo CON_RECETA
    fun pideReceta(condicion: String?): Boolean = CondicionVenta.desde(condicion) == CondicionVenta.CON_RECETA
}

// H72: etiqueta "Requiere receta médica" o "Receta retenida"; no dibuja nada en los demás casos
@Composable
fun EtiquetaCondicionVenta(condicion: String?, modifier: Modifier = Modifier) {
    val texto = ReglasCondicionVenta.etiqueta(condicion) ?: return
    val retenida = ReglasCondicionVenta.esRecetaRetenida(condicion)
    val extendidos = BotiComparaTheme.extendedColors
    Etiqueta(
        texto = texto,
        modifier = modifier,
        icono = if (retenida) Icons.Rounded.Lock else Icons.Rounded.Description,
        fondo = if (retenida) MaterialTheme.colorScheme.errorContainer else extendidos.alertaContainer,
        color = if (retenida) MaterialTheme.colorScheme.onErrorContainer else extendidos.onAlertaContainer
    )
}

// H72: aviso en bloque (hoja de reserva, detalle del producto y de la reserva)
@Composable
fun AvisoReceta(texto: String, retenida: Boolean, modifier: Modifier = Modifier) {
    val extendidos = BotiComparaTheme.extendedColors
    val fondo = if (retenida) MaterialTheme.colorScheme.errorContainer else extendidos.alertaContainer
    val contenido = if (retenida) MaterialTheme.colorScheme.onErrorContainer else extendidos.onAlertaContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(fondo)
            .padding(horizontal = 14.dp, vertical = Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (retenida) Icons.Rounded.Lock else Icons.Rounded.Description,
            contentDescription = null,
            tint = contenido,
            modifier = Modifier.size(Dimens.IconMedium)
        )
        Spacer(Modifier.width(Dimens.Space12))
        Text(texto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = contenido)
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun CondicionVentaPreview() {
    BotiComparaAppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtiquetaCondicionVenta("CON_RECETA")
                EtiquetaCondicionVenta("RECETA_RETENIDA")
                AvisoReceta(ReglasCondicionVenta.AVISO_CON_RECETA, retenida = false)
                AvisoReceta(ReglasCondicionVenta.AVISO_RECETA_RETENIDA, retenida = true)
            }
        }
    }
}
