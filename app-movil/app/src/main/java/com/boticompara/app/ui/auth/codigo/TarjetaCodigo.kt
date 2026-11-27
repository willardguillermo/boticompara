package com.boticompara.app.ui.auth.codigo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.components.TarjetaVidrio
import com.boticompara.app.ui.theme.Dimens
import com.boticompara.app.ui.theme.LocalExtendedColors

/**
 * H17, H20 - Tarjeta de vidrio para escribir el código que llegó al correo. La usan el registro (paso 2),
 * el login (403 CORREO_NO_VERIFICADO) y la recuperación de la contraseña (con los campos de la contraseña
 * nueva en `campos`). Al verificar, la tarjeta se tiñe de verde. "¿No te llegó el código? Reenviar en 00:38"
 * usa la espera de `estado` (60 s o el Retry-After del servidor).
 */
@Composable
fun TarjetaCodigo(
    estado: VerificacionCodigoEstado,
    titulo: String,
    subtitulo: AnnotatedString,
    onCambio: (String) -> Unit,
    onVerificar: () -> Unit,
    onReenviar: () -> Unit,
    modifier: Modifier = Modifier,
    tituloExito: String = "Correo verificado",
    subtituloExito: String? = null,
    textoBoton: String = "Verificar",
    textoExito: String = "Verificado",
    textoCambiar: String? = null,
    onCambiar: (() -> Unit)? = null,
    campos: (@Composable ColumnScope.() -> Unit)? = null
) {
    val reducido = rememberMovimientoReducido()
    SideEffect { estado.movimientoReducido = reducido }
    val esquema = MaterialTheme.colorScheme
    val exito = estado.exito

    // Tinte verde de la tarjeta: se lee al dibujar
    val tinte = remember { Animatable(0f) }
    LaunchedEffect(exito) { tinte.animateTo(if (exito) 1f else 0f, tween(if (reducido) 200 else 450)) }

    TarjetaVidrio(modifier = modifier, tinte = { tinte.value }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Crossfade(exito, animationSpec = tween(250), label = "icono") { listo ->
                IconoCircular(
                    icono = if (listo) Icons.Rounded.VerifiedUser else Icons.Rounded.MarkEmailUnread,
                    tamano = 64.dp,
                    fondo = if (listo) LocalExtendedColors.current.exitoContainer else esquema.primaryContainer,
                    color = if (listo) LocalExtendedColors.current.onExitoContainer else esquema.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(Dimens.Space12))
            AnimatedContent(
                targetState = if (exito) tituloExito else titulo,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "titulo"
            ) { texto ->
                Text(
                    texto,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Spacer(Modifier.height(Dimens.Space4))
            if (exito && subtituloExito != null) {
                Text(
                    subtituloExito,
                    style = MaterialTheme.typography.bodyMedium,
                    color = esquema.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = esquema.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        estado.mensaje?.let { Aviso(it, esError = false) }
        estado.errorGeneral?.let { Aviso(it, esError = true) }

        Spacer(Modifier.height(Dimens.Space16))
        Text(
            "Código de 6 dígitos",
            style = MaterialTheme.typography.labelLarge,
            color = esquema.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.Space4))
        CampoCodigo(
            valor = estado.codigo,
            onCambio = onCambio,
            fase = estado.fase,
            error = estado.errorCodigo,
            sacudidas = estado.sacudidas,
            reducido = reducido,
            // Con los campos de la contraseña debajo, "Siguiente" pasa a ellos; si no, "Listo" verifica
            imeAction = if (campos != null) ImeAction.Next else ImeAction.Done,
            onListo = onVerificar
        )

        // El error del código y lo que pasa ("Verificando código", "Correo verificado"), para TalkBack
        Text(
            estado.errorCodigo ?: "",
            color = esquema.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive }
        )
        Text(
            estado.anuncio.let { if (it.isEmpty()) "" else if (estado.exito) "$it." else "$it…" },
            color = if (estado.exito) LocalExtendedColors.current.exito else esquema.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
        )

        if (campos != null) {
            Spacer(Modifier.height(Dimens.Space8))
            campos()
        }

        Spacer(Modifier.height(Dimens.Space12))
        BotiButton(
            text = when {
                exito -> textoExito
                estado.ocupado -> "Verificando código…"
                else -> textoBoton
            },
            icon = if (exito) Icons.Rounded.Check else null,
            onClick = onVerificar,
            enabled = !estado.ocupado,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Dimens.Space8))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("¿No te llegó el código?", style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onReenviar,
                enabled = !estado.reenviando && !estado.ocupado && !estado.espera.activa
            ) {
                Text(
                    when {
                        estado.reenviando -> "Reenviando…"
                        estado.espera.activa -> "Reenviar en ${formatoReloj(estado.espera.segundos)}"
                        else -> "Reenviar"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (textoCambiar != null && onCambiar != null) {
            TextButton(
                onClick = onCambiar,
                enabled = !estado.ocupado,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(textoCambiar)
            }
        }
    }
}

@Composable
private fun Aviso(texto: String, esError: Boolean) {
    val esquema = MaterialTheme.colorScheme
    Spacer(Modifier.height(Dimens.Space12))
    Surface(
        color = if (esError) esquema.errorContainer else esquema.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(
            texto,
            color = if (esError) esquema.onErrorContainer else esquema.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Dimens.Space12)
        )
    }
}
