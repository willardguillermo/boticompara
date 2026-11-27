package com.boticompara.app.ui.auth.codigo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.theme.LocalExtendedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * H20 - Insignia del paso "Tu cuenta" del registro: aparece con un pequeño resorte, el check se dibuja,
 * un brillo pasa una vez y dice "Correo verificado" con el correo debajo.
 * Con las animaciones del sistema apagadas aparece quieta.
 */
@Composable
fun InsigniaVerificado(correo: String, modifier: Modifier = Modifier) {
    val reducido = rememberMovimientoReducido()
    val colores = LocalExtendedColors.current
    val aparicion = remember { Animatable(if (reducido) 1f else 0f) }
    val trazo = remember { Animatable(if (reducido) 1f else 0f) }
    val brillo = remember { Animatable(if (reducido) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reducido) return@LaunchedEffect
        launch { aparicion.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
        delay(160)
        trazo.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        brillo.animateTo(1f, tween(750, easing = LinearEasing))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Correo verificado: $correo" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .graphicsLayer {
                    val a = aparicion.value
                    alpha = a.coerceIn(0f, 1f)
                    val escala = 0.82f + 0.18f * a
                    scaleX = escala
                    scaleY = escala
                }
                .clip(CircleShape)
                .background(colores.exitoContainer)
                .drawWithContent {
                    drawContent()
                    // Un brillo que cruza la insignia una vez
                    val b = brillo.value
                    if (b > 0f && b < 1f) {
                        val x = -size.width * 0.3f + size.width * 1.6f * b
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                                start = Offset(x - 36.dp.toPx(), 0f),
                                end = Offset(x + 36.dp.toPx(), size.height)
                            )
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(colores.exito, CircleShape)
                    .drawWithCache {
                        val ruta = Path().apply {
                            moveTo(size.width * 0.27f, size.height * 0.52f)
                            lineTo(size.width * 0.44f, size.height * 0.68f)
                            lineTo(size.width * 0.74f, size.height * 0.36f)
                        }
                        val medida = PathMeasure().apply { setPath(ruta, false) }
                        val parcial = Path()
                        val grosor = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        onDrawWithContent {
                            drawContent()
                            parcial.reset()
                            medida.getSegment(0f, medida.length * trazo.value, parcial, true)
                            drawPath(parcial, colores.onExito, style = grosor)
                        }
                    }
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Correo verificado",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colores.onExitoContainer
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            correo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
