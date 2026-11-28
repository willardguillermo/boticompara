package com.boticompara.app.ui.auth.codigo

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.boticompara.app.ui.theme.LocalExtendedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

// 45 LEDs en una cuadrícula de 9 x 9 con brazos de 3, como el logo
private val LEDS = puntosCruz(9, 3)
private const val ANILLOS = 4f
private val ALTO_ESCENA = 84.dp
private val TAM_CRUZ = 78.dp

/**
 * Las animaciones del sistema están apagadas (Opciones de desarrollador o Accesibilidad → escala 0):
 * la verificación solo cambia de estado con fundidos, sin movimiento.
 */
@Composable
fun rememberMovimientoReducido(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * H17, H20 - El código de 6 dígitos en 6 casillas (registro, login y recuperar la contraseña).
 * Detrás hay un único campo de texto: el teclado numérico, pegar el código (se reparte en las casillas),
 * el autocompletado del código (ContentType.SmsOtpCode) y Backspace (retrocede) funcionan como en
 * cualquier campo. Las casillas solo dibujan ese texto.
 *
 * La verificación se dibuja con la cruz LED de botica: las casillas se juntan al centro y forman una cruz
 * de 45 puntos; una onda de luz va del centro a las puntas mientras se espera; si es correcto queda fija y se
 * vuelve un check con 2 ondas; si falla, parpadea 2 veces en rojo y vuelve a las 6 casillas con un rebote.
 * Todo se anima con graphicsLayer (escala, traslación, alpha) o al dibujar el Canvas: los Animatable se leen
 * solo dentro de esas lambdas, así no se recompone el layout en cada frame.
 */
@Composable
fun CampoCodigo(
    valor: String,
    onCambio: (String) -> Unit,
    fase: FaseCodigo,
    error: String?,
    sacudidas: Int,
    reducido: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onListo: () -> Unit = {},
    autoFoco: Boolean = true
) {
    val esquema = MaterialTheme.colorScheme
    val exito = LocalExtendedColors.current.exito
    val densidad = LocalDensity.current
    val teclado = LocalSoftwareKeyboardController.current
    val foco = remember { FocusRequester() }
    var enfocado by remember { mutableStateOf(false) }
    var campo by remember { mutableStateOf(TextFieldValue(valor, TextRange(valor.length))) }
    // Si el estado cambió desde fuera (se limpió tras un error o un reenvío), manda el valor del estado
    val mostrado = if (campo.text == valor) campo else TextFieldValue(valor, TextRange(valor.length))
    val ocupado = fase != FaseCodigo.ESCRIBIENDO

    // ---- Animaciones (0 a 1) ----
    val reunir = remember { Animatable(0f) }    // casillas → centro
    val cruz = remember { Animatable(0f) }      // aparece la cruz
    val onda = remember { Animatable(0f) }      // frente de la onda de luz (se repite)
    val fija = remember { Animatable(0f) }      // todos los LEDs encendidos
    val rojo = remember { Animatable(0f) }      // falla
    val check = remember { Animatable(0f) }     // trazo del check
    val ondasExito = remember { Animatable(0f) } // las 2 ondas del check
    val sacudida = remember { Animatable(0f) }  // px

    LaunchedEffect(autoFoco) {
        if (autoFoco) {
            foco.requestFocus()
            teclado?.show()
        }
    }

    LaunchedEffect(fase, reducido) {
        when (fase) {
            FaseCodigo.ESCRIBIENDO -> {
                launch { cruz.animateTo(0f, tween(150)) }
                check.snapTo(0f)
                ondasExito.snapTo(0f)
                rojo.snapTo(0f)
                fija.snapTo(0f)
                if (reunir.value != 0f) {
                    reunir.animateTo(0f, if (reducido) tween(200) else spring(dampingRatio = 0.45f, stiffness = 380f))
                }
            }
            FaseCodigo.REUNIR -> {
                rojo.snapTo(0f)
                check.snapTo(0f)
                ondasExito.snapTo(0f)
                if (reducido) {
                    // Solo fundidos: las casillas se apagan y la cruz aparece quieta, a media luz
                    fija.snapTo(0.55f)
                    launch { reunir.animateTo(1f, tween(200)) }
                    cruz.animateTo(1f, tween(200))
                } else {
                    fija.snapTo(0f)
                    launch { reunir.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
                    launch {
                        delay(200)
                        cruz.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                    }
                    delay(240)
                    while (true) {
                        onda.snapTo(0f)
                        onda.animateTo(1f, tween(950, easing = LinearEasing))
                    }
                }
            }
            FaseCodigo.FIJA -> {
                reunir.snapTo(1f)
                launch { cruz.animateTo(1f, tween(120)) }
                fija.animateTo(1f, tween(if (reducido) 150 else 220))
            }
            FaseCodigo.EXITO -> {
                teclado?.hide()
                reunir.snapTo(1f)
                fija.snapTo(1f)
                launch { cruz.animateTo(0f, tween(if (reducido) 200 else 220)) }
                if (reducido) {
                    check.animateTo(1f, tween(250))
                } else {
                    launch { ondasExito.animateTo(1f, tween(950, delayMillis = 180, easing = LinearOutSlowInEasing)) }
                    check.animateTo(1f, tween(480, delayMillis = 120, easing = FastOutSlowInEasing))
                }
            }
            FaseCodigo.FALLA -> {
                reunir.snapTo(1f)
                cruz.snapTo(1f)
                fija.snapTo(1f)
                if (reducido) {
                    rojo.animateTo(1f, tween(200))
                } else {
                    // Parpadea 2 veces en rojo
                    rojo.animateTo(1f, keyframes {
                        durationMillis = 560
                        0f at 0
                        1f at 60
                        0.12f at 170
                        1f at 270
                        0.12f at 380
                        1f at 480
                    })
                }
            }
            FaseCodigo.VOLVER -> {
                launch { cruz.animateTo(0f, tween(160)) }
                if (reducido) {
                    reunir.animateTo(0f, tween(200))
                } else {
                    delay(60)
                    // Rebote: el resorte pasa un poco de su lugar y vuelve
                    reunir.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 420f))
                }
            }
        }
    }

    // Código incorrecto: sacudida suave y el foco vuelve a la primera casilla
    LaunchedEffect(sacudidas) {
        if (sacudidas == 0) return@LaunchedEffect
        foco.requestFocus()
        teclado?.show()
        if (!reducido) {
            val px = with(densidad) { 1.dp.toPx() }
            sacudida.snapTo(0f)
            sacudida.animateTo(0f, keyframes {
                durationMillis = 420
                0f at 0
                -7f * px at 60
                6f * px at 140
                -4f * px at 220
                2f * px at 300
                0f at 420
            })
        }
    }

    val casillas = ReglasCodigo.casillas(valor)
    val activa = ReglasCodigo.casillaActiva(valor)
    val descripcion = when {
        fase == FaseCodigo.EXITO -> "Código verificado"
        ocupado -> "Verificando código"
        valor.isEmpty() -> "Vacío"
        else -> "${valor.length} de ${ReglasCodigo.LARGO} dígitos: ${valor.toList().joinToString(" ")}"
    }

    BasicTextField(
        value = mostrado,
        onValueChange = { nuevo ->
            if (ocupado) return@BasicTextField
            // El cursor siempre al final: Backspace borra el último dígito y la casilla activa retrocede
            campo = nuevo.copy(selection = TextRange(nuevo.text.length))
            onCambio(nuevo.text)
        },
        readOnly = ocupado,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onListo() }),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(foco)
            .onFocusChanged { enfocado = it.isFocused }
            .semantics {
                contentType = ContentType.SmsOtpCode
                contentDescription = "Código de 6 dígitos"
                stateDescription = descripcion
                if (error != null) error(error)
            },
        decorationBox = { campoInterno ->
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(ALTO_ESCENA),
                contentAlignment = Alignment.Center
            ) {
                val separacion = 8.dp
                val ancho = min(52.dp, (maxWidth - separacion * 5) / 6)
                val alto = min(60.dp, ancho * 1.22f)
                // Cuánto se mueve cada casilla para llegar al centro de la fila (se calcula una vez por tamaño)
                val desplazamientos = remember(ancho) {
                    with(densidad) {
                        val paso = (ancho + separacion).toPx()
                        FloatArray(ReglasCodigo.LARGO) { i -> (2.5f - i) * paso }
                    }
                }

                // El campo real queda detrás, invisible (recibe el texto, el foco y el autocompletado)
                Box(Modifier.size(1.dp).graphicsLayer { alpha = 0f }) { campoInterno() }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(separacion),
                    modifier = Modifier
                        .clearAndSetSemantics { }
                        .graphicsLayer { translationX = sacudida.value }
                ) {
                    casillas.forEachIndexed { i, digito ->
                        Casilla(
                            digito = digito,
                            activa = enfocado && !ocupado && i == activa,
                            llena = digito != null,
                            conError = error != null,
                            modifier = Modifier
                                .size(ancho, alto)
                                .graphicsLayer {
                                    val r = reunir.value
                                    if (!reducido) {
                                        translationX = desplazamientos[i] * r
                                        val escala = 1f - 0.55f * r
                                        scaleX = escala
                                        scaleY = escala
                                    }
                                    alpha = (1f - r).coerceIn(0f, 1f)
                                }
                        )
                    }
                }

                CruzLed(
                    onda = { onda.value },
                    fija = { fija.value },
                    rojo = { rojo.value },
                    apagado = esquema.primary.copy(alpha = 0.16f),
                    encendido = esquema.primary,
                    brillo = esquema.primaryContainer,
                    colorFalla = esquema.error,
                    modifier = Modifier
                        .size(TAM_CRUZ)
                        .clearAndSetSemantics { }
                        .graphicsLayer {
                            val c = cruz.value
                            alpha = c.coerceIn(0f, 1f)
                            if (!reducido) {
                                val escala = 0.6f + 0.4f * c
                                scaleX = escala
                                scaleY = escala
                            }
                        }
                )

                CheckExito(
                    progreso = { check.value },
                    ondas = { ondasExito.value },
                    reducido = reducido,
                    color = exito,
                    colorTrazo = LocalExtendedColors.current.onExito,
                    modifier = Modifier
                        .size(TAM_CRUZ)
                        .clearAndSetSemantics { }
                )
            }
        }
    )
}

/** Una casilla: la activa lleva el borde y el halo verde azulado de la marca. */
@Composable
private fun Casilla(
    digito: Char?,
    activa: Boolean,
    llena: Boolean,
    conError: Boolean,
    modifier: Modifier
) {
    val esquema = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(14.dp)
    // El halo se lee al dibujar: la animación no recompone la casilla
    val halo by animateFloatAsState(if (activa) 1f else 0f, tween(160), label = "halo")
    val colorHalo = esquema.primary
    val borde = when {
        conError -> esquema.error
        activa -> esquema.primary
        llena -> esquema.primary.copy(alpha = 0.45f)
        else -> esquema.outlineVariant
    }
    Box(
        modifier
            .drawBehind {
                if (halo > 0f) {
                    val extra = 4.dp.toPx() * halo
                    drawRoundRect(
                        color = colorHalo.copy(alpha = 0.20f * halo),
                        topLeft = Offset(-extra, -extra),
                        size = androidx.compose.ui.geometry.Size(size.width + extra * 2, size.height + extra * 2),
                        cornerRadius = CornerRadius(14.dp.toPx() + extra)
                    )
                }
            }
            .background(esquema.surfaceContainerLowest.copy(alpha = 0.9f), forma)
            .border(if (activa || conError) 2.dp else 1.dp, borde, forma),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digito?.toString() ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (conError) esquema.error else esquema.onSurface
        )
    }
}

/** La cruz de 45 LEDs. Todo se calcula al dibujar con los valores de las animaciones. */
@Composable
private fun CruzLed(
    onda: () -> Float,
    fija: () -> Float,
    rojo: () -> Float,
    apagado: Color,
    encendido: Color,
    brillo: Color,
    colorFalla: Color,
    modifier: Modifier
) {
    Canvas(modifier) {
        val paso = size.minDimension / 9f
        val radio = paso * 0.34f
        // El frente de la onda recorre los anillos 0 (centro) a 4 (puntas) y un poco más, para salir del todo
        val frente = onda() * (ANILLOS + 2.2f) - 1f
        val todos = fija()
        val falla = rojo()
        for (led in LEDS) {
            val ola = (1f - abs(led.anillo - frente) / 1.3f).coerceIn(0f, 1f)
            val luz = max(todos, 0.18f + 0.82f * ola)
            var color = lerp(apagado, encendido, luz)
            if (falla > 0f) color = lerp(color, colorFalla, falla)
            val centro = Offset(paso * (led.columna + 0.5f), paso * (led.fila + 0.5f))
            if (luz > 0.55f) {
                // El resplandor del LED encendido
                val halo = if (falla > 0.5f) colorFalla else brillo
                drawCircle(halo.copy(alpha = 0.35f * luz), radius = radio * 2f, center = centro)
            }
            drawCircle(color, radius = radio, center = centro)
        }
    }
}

/** El check que se dibuja (trazo) con 2 ondas que se abren. */
@Composable
private fun CheckExito(
    progreso: () -> Float,
    ondas: () -> Float,
    reducido: Boolean,
    color: Color,
    colorTrazo: Color,
    modifier: Modifier
) {
    Box(
        modifier.drawWithCache {
            val r = size.minDimension * 0.36f
            val c = Offset(size.width / 2f, size.height / 2f)
            val trazo = Path().apply {
                moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
                lineTo(c.x - r * 0.12f, c.y + r * 0.32f)
                lineTo(c.x + r * 0.45f, c.y - r * 0.30f)
            }
            val medida = PathMeasure().apply { setPath(trazo, false) }
            val parcial = Path()
            val grosor = Stroke(width = r * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val grosorOnda = Stroke(width = 2.dp.toPx())
            onDrawBehind {
                val p = progreso()
                if (p <= 0f) return@onDrawBehind
                val o = ondas()
                for (k in 0..1) {
                    val t = ((o - k * 0.28f) / 0.72f).coerceIn(0f, 1f)
                    if (t > 0f && t < 1f) {
                        drawCircle(color.copy(alpha = 0.55f * (1f - t)), radius = r * (1f + 0.75f * t), center = c, style = grosorOnda)
                    }
                }
                val aparicion = if (reducido) p else (p * 2.2f).coerceAtMost(1f)
                drawCircle(color.copy(alpha = aparicion), radius = r * (if (reducido) 1f else 0.82f + 0.18f * aparicion), center = c)
                parcial.reset()
                val largo = if (reducido) medida.length else medida.length * ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
                if (largo > 0f) {
                    medida.getSegment(0f, largo, parcial, true)
                    drawPath(parcial, colorTrazo.copy(alpha = aparicion), style = grosor)
                }
            }
        }
    )
}
