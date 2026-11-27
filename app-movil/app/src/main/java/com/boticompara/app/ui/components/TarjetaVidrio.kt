package com.boticompara.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.theme.BotiComparaAppTheme
import com.boticompara.app.ui.theme.LocalExtendedColors

/**
 * H20: tarjeta de vidrio de los pasos del acceso (registro, verificación y recuperación), en claro y en oscuro.
 * Superficie translúcida con un brillo arriba y dos luces de la marca detrás. No usa blur (en Android 11 o
 * menos no existe): las luces son degradados radiales, que cuestan poco.
 * `tinte` (0 a 1) la tiñe de verde al verificar; se lee al dibujar, así animarlo no recompone la tarjeta.
 */
@Composable
fun TarjetaVidrio(
    modifier: Modifier = Modifier,
    tinte: () -> Float = { 0f },
    content: @Composable ColumnScope.() -> Unit
) {
    val esquema = MaterialTheme.colorScheme
    val oscuro = esquema.surface.luminance() < 0.5f
    val exito = LocalExtendedColors.current.exito
    val forma = RoundedCornerShape(28.dp)
    val luz1 = esquema.primary.copy(alpha = if (oscuro) 0.30f else 0.22f)
    val luz2 = esquema.tertiary.copy(alpha = if (oscuro) 0.22f else 0.16f)

    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(luz1, Color.Transparent), Offset(size.width * 0.12f, 0f), size.width * 0.62f),
                    radius = size.width * 0.62f,
                    center = Offset(size.width * 0.12f, 0f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(luz2, Color.Transparent), Offset(size.width * 0.92f, size.height), size.width * 0.55f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.92f, size.height)
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(if (oscuro) 0.dp else 10.dp, forma, ambientColor = esquema.primary, spotColor = esquema.primary)
                .clip(forma)
                .background(esquema.surfaceContainerLowest.copy(alpha = if (oscuro) 0.58f else 0.72f))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (oscuro) 0.07f else 0.55f), Color.Transparent)
                    )
                )
                .drawBehind {
                    val t = tinte()
                    if (t > 0f) drawRect(exito.copy(alpha = (if (oscuro) 0.20f else 0.14f) * t))
                }
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (oscuro) 0.16f else 0.85f),
                            esquema.outlineVariant.copy(alpha = 0.55f)
                        )
                    ),
                    forma
                )
                .padding(20.dp),
            content = content
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun TarjetaVidrioPreview() {
    BotiComparaAppTheme {
        Surface {
            Box(Modifier.padding(24.dp)) {
                TarjetaVidrio {
                    Text("Verifica tu correo", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
