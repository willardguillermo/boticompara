package com.boticompara.app.ui.resenas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boticompara.app.data.remote.dto.ResenaResponse
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.BotiTextField
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaResena(
    miResena: ResenaResponse?,
    guardando: Boolean,
    mensajeError: String?,
    onGuardar: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var estrellas by remember(miResena?.id) { mutableStateOf(miResena?.estrellas ?: 0) }
    var comentario by remember(miResena?.id) { mutableStateOf(miResena?.comentario.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = { if (!guardando) onDismiss() },
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Space24)
                .padding(bottom = Dimens.Space24)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Califica esta botica",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Dimens.Space4))
            Text(
                "Tu opinión ayuda a otros compradores",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Dimens.Space24))
            Text("Estrellas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Dimens.Space8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { valor ->
                    IconButton(
                        onClick = { estrellas = valor },
                        enabled = !guardando,
                        modifier = Modifier
                            .size(Dimens.MinTouchTarget)
                            .semantics {
                                contentDescription = if (valor == 1) "1 estrella" else "$valor estrellas"
                            }
                    ) {
                        Icon(
                            imageVector = if (valor <= estrellas) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = null,
                            tint = if (valor <= estrellas) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimens.IconLarge)
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.Space4))
            Text(
                if (estrellas == 0) "Elige una calificación"
                else ReglasResena.textoEstrellas(estrellas),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (miResena?.oculta == true) {
                Spacer(Modifier.height(Dimens.Space16))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(Dimens.Space16),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconoCircular(
                            icono = Icons.Rounded.StarBorder,
                            tamano = Dimens.IconLarge,
                            fondo = MaterialTheme.colorScheme.onErrorContainer,
                            color = MaterialTheme.colorScheme.errorContainer
                        )
                        Spacer(Modifier.width(Dimens.Space12))
                        Text(
                            "Tu reseña fue ocultada por el administrador. Puedes editarla, pero seguirá sin mostrarse.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.Space20))
            BotiTextField(
                value = comentario,
                onValueChange = {
                    if (it.length <= ReglasResena.MAX_COMENTARIO) comentario = it
                },
                label = "Comentario (opcional)",
                modifier = Modifier.height(120.dp),
                supportingText = "${comentario.length}/${ReglasResena.MAX_COMENTARIO}",
                singleLine = false,
                enabled = !guardando
            )

            if (mensajeError != null) {
                Spacer(Modifier.height(Dimens.Space12))
                Text(
                    mensajeError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            Spacer(Modifier.height(Dimens.Space20))
            BotiButton(
                text = if (miResena != null) "Actualizar reseña" else "Publicar reseña",
                onClick = { onGuardar(estrellas, comentario) },
                enabled = !guardando && ReglasResena.estrellasValidas(estrellas),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
