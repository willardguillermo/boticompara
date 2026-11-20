package com.boticompara.app.ui.reservas

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.components.Etiqueta
import com.boticompara.app.ui.theme.BotiComparaAppTheme
import com.boticompara.app.ui.theme.BotiComparaTheme
import com.boticompara.app.ui.theme.ExtendedColors

data class ColoresEstado(val fondo: Color, val contenido: Color)

// H76: color de cada tono con los tokens del tema (claro y oscuro)
fun coloresDe(tono: TonoEstado?, esquema: ColorScheme, extendidos: ExtendedColors): ColoresEstado =
    when (tono) {
        TonoEstado.ALERTA -> ColoresEstado(extendidos.alertaContainer, extendidos.onAlertaContainer)
        TonoEstado.INFORMACION -> ColoresEstado(esquema.tertiaryContainer, esquema.onTertiaryContainer)
        TonoEstado.EXITO -> ColoresEstado(extendidos.exitoContainer, extendidos.onExitoContainer)
        TonoEstado.PRIMARIO -> ColoresEstado(esquema.primaryContainer, esquema.onPrimaryContainer)
        TonoEstado.ERROR -> ColoresEstado(esquema.errorContainer, esquema.onErrorContainer)
        TonoEstado.VENCIDO -> ColoresEstado(extendidos.stockBajoContainer, extendidos.onStockBajoContainer)
        TonoEstado.NEUTRO, null -> ColoresEstado(esquema.surfaceContainerHighest, esquema.onSurfaceVariant)
    }

private fun iconoDe(estado: EstadoReserva?): ImageVector = when (estado) {
    EstadoReserva.PENDIENTE -> Icons.Rounded.HourglassEmpty
    EstadoReserva.CONFIRMADA -> Icons.Rounded.ThumbUp
    EstadoReserva.LISTA -> Icons.Rounded.Inventory2
    EstadoReserva.ENTREGADA -> Icons.Rounded.TaskAlt
    EstadoReserva.RECHAZADA -> Icons.Rounded.Block
    EstadoReserva.CANCELADA -> Icons.Rounded.Close
    EstadoReserva.VENCIDA -> Icons.Rounded.Schedule
    null -> Icons.Rounded.CheckCircle
}

// H76: chip de color con el nombre del estado en español
@Composable
fun EstadoReservaChip(estado: String, modifier: Modifier = Modifier) {
    val conocido = EstadoReserva.desde(estado)
    val colores = coloresDe(conocido?.tono, MaterialTheme.colorScheme, BotiComparaTheme.extendedColors)
    Etiqueta(
        texto = EstadoReserva.nombreDe(estado),
        modifier = modifier,
        icono = iconoDe(conocido),
        fondo = colores.fondo,
        color = colores.contenido
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun EstadoReservaChipPreview() {
    BotiComparaAppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EstadoReserva.entries.forEach { EstadoReservaChip(it.name) }
            }
        }
    }
}
