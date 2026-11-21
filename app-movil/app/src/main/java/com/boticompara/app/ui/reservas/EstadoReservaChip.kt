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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.components.Etiqueta
import com.boticompara.app.ui.theme.BotiComparaAppTheme

data class ColoresEstado(val fondo: Color, val contenido: Color)

// H72-H76: mismos colores que la web del dueño (web-usuario/src/index.css, .estado-pedido-*).
// Modo claro: los hex de la web. Modo oscuro: el mismo tono, fondo oscuro y texto claro.
// Todos cumplen el contraste AA (4.5:1) entre texto y fondo.
object ColoresReserva {
    private val claro = mapOf(
        TonoEstado.ALERTA to ColoresEstado(Color(0xFFFEF3C7), Color(0xFFB45309)),
        TonoEstado.INFORMACION to ColoresEstado(Color(0xFFDBEAFE), Color(0xFF1D4ED8)),
        TonoEstado.PRIMARIO to ColoresEstado(Color(0xFFCCFBF1), Color(0xFF115E59)),
        TonoEstado.EXITO to ColoresEstado(Color(0xFFDCFCE7), Color(0xFF15803D)),
        TonoEstado.ERROR to ColoresEstado(Color(0xFFFEE2E2), Color(0xFFB91C1C)),
        TonoEstado.NEUTRO to ColoresEstado(Color(0xFFEEF2F1), Color(0xFF557069))
    )
    private val oscuro = mapOf(
        TonoEstado.ALERTA to ColoresEstado(Color(0xFF451A03), Color(0xFFFCD34D)),
        TonoEstado.INFORMACION to ColoresEstado(Color(0xFF172554), Color(0xFFBFDBFE)),
        TonoEstado.PRIMARIO to ColoresEstado(Color(0xFF134E4A), Color(0xFF99F6E4)),
        TonoEstado.EXITO to ColoresEstado(Color(0xFF14532D), Color(0xFFBBF7D0)),
        TonoEstado.ERROR to ColoresEstado(Color(0xFF7F1D1D), Color(0xFFFECACA)),
        TonoEstado.NEUTRO to ColoresEstado(Color(0xFF2D3634), Color(0xFFC6D4D0))
    )

    fun de(tono: TonoEstado?, modoOscuro: Boolean): ColoresEstado =
        (if (modoOscuro) oscuro else claro).getValue(tono ?: TonoEstado.NEUTRO)
}

// Un estado desconocido se pinta como neutro
fun coloresDe(tono: TonoEstado?, modoOscuro: Boolean): ColoresEstado = ColoresReserva.de(tono, modoOscuro)

// El tema oscuro se reconoce por la superficie (respeta el tema elegido, no solo el del sistema)
@Composable
fun esModoOscuro(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
    val colores = coloresDe(conocido?.tono, esModoOscuro())
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
