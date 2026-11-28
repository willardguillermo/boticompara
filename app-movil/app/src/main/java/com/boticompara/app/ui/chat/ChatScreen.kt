package com.boticompara.app.ui.chat

import com.boticompara.app.ui.navigation.LocalEspacioBarra
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.remote.dto.ProductoChatResponse
import com.boticompara.app.ui.components.BotiTextField
import com.boticompara.app.ui.components.CabeceraPantalla
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.components.LineaConIcono
import com.boticompara.app.ui.theme.Dimens
import java.util.Locale

private val SUGERENCIAS = listOf(
    "¿Dónde está más barato el paracetamol?",
    "Busca ibuprofeno de 400 miligramos",
    "¿Hay una alternativa más barata a la amoxicilina?"
)

// H70: pantalla del asistente de texto
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = viewModel()
) {
    val listState = rememberLazyListState()
    val mensajes = viewModel.mensajes
    val snackbarHostState = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val permisoMicrofono = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) viewModel.iniciarEscucha() else viewModel.permisoMicrofonoDenegado()
    }
    val alternarMicrofono: () -> Unit = {
        when {
            viewModel.escuchando -> viewModel.detenerEscucha()
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.iniciarEscucha()
            else -> permisoMicrofono.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(viewModel.avisoVoz) {
        viewModel.avisoVoz?.let { aviso ->
            snackbarHostState.showSnackbar(aviso)
            viewModel.avisoVozAtendido()
        }
    }

    LaunchedEffect(viewModel.sesionVencida) {
        if (viewModel.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            viewModel.sesionVencidaAtendida()
        }
    }

    // Baja hasta el último mensaje (o hasta el "Escribiendo…") cuando algo cambia
    LaunchedEffect(mensajes.size, viewModel.enviando) {
        val total = mensajes.size + if (viewModel.enviando) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = LocalEspacioBarra.current)) },
        topBar = {
            CabeceraPantalla(
                titulo = "Asistente",
                subtitulo = "Pregunta por precios y boticas",
                actions = {
                    if (viewModel.hablando) {
                        IconButton(onClick = { viewModel.callar() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = "Silenciar la voz"
                            )
                        }
                    }
                    if (mensajes.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.nuevaConversacion() },
                            enabled = !viewModel.enviando
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Nueva conversación")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // La caja de texto queda encima de la barra flotante; con el teclado abierto, encima del teclado
                .padding(bottom = LocalEspacioBarra.current)
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (mensajes.isEmpty() && !viewModel.enviando) {
                    Bienvenida(onSugerencia = { viewModel.enviar(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Dimens.Space16),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Space12)
                    ) {
                        items(mensajes, key = { it.id }) { mensaje ->
                            Burbuja(
                                mensaje = mensaje,
                                onProductoClick = { id -> navController.navigate("detalle/$id") }
                            )
                        }
                        if (viewModel.enviando) {
                            item { Escribiendo() }
                        }
                    }
                }
            }
            BarraEscritura(
                texto = if (viewModel.escuchando) viewModel.textoParcial else viewModel.texto,
                onTextoChange = viewModel::cambiarTexto,
                onEnviar = { viewModel.enviarEscrito() },
                onMicrofono = alternarMicrofono,
                escuchando = viewModel.escuchando,
                puedeEnviar = viewModel.texto.isNotBlank() &&
                    !viewModel.enviando && !viewModel.escuchando
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Bienvenida(onSugerencia: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconoCircular(icono = Icons.Rounded.SmartToy, tamano = 80.dp)
        Spacer(Modifier.height(Dimens.Space16))
        Text(
            "Hola, soy el asistente de BotiCompara",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.Space4))
        Text(
            "Te ayudo a comparar precios y encontrar boticas. Toca el micrófono para hablarme y te responderé en voz alta. No doy consejos médicos ni dosis.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.Space20))
        SUGERENCIAS.forEach { sugerencia ->
            SuggestionChip(
                onClick = { onSugerencia(sugerencia) },
                label = { Text(sugerencia) },
                modifier = Modifier.padding(vertical = Dimens.Space4)
            )
        }
    }
}

@Composable
private fun Burbuja(mensaje: MensajeChat, onProductoClick: (Long) -> Unit) {
    val colores = when {
        mensaje.esUsuario -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        mensaje.esError || mensaje.esAyuda ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    val forma = if (mensaje.esUsuario) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mensaje.esUsuario) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8)
    ) {
        Surface(
            color = colores.first,
            contentColor = colores.second,
            shape = forma,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                mensaje.texto,
                modifier = Modifier.padding(horizontal = Dimens.Space16, vertical = Dimens.Space12),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        mensaje.productos.forEach { producto ->
            ProductoChatCard(producto = producto, onClick = { onProductoClick(producto.productoId) })
        }
    }
}

@Composable
private fun ProductoChatCard(producto: ProductoChatResponse, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Dimens.Space12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        producto.nombreComercial,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        producto.presentacion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(Dimens.Space8))
                Text(
                    "S/ ${String.format(Locale.US, "%.2f", producto.precio)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(Dimens.Space8))
            LineaConIcono(
                icono = Icons.Rounded.LocalPharmacy,
                texto = "${producto.boticaNombre}, ${producto.boticaDistrito}"
            )
            if (producto.distanciaKm != null) {
                LineaConIcono(
                    icono = Icons.Rounded.NearMe,
                    texto = "A ${String.format(Locale.US, "%.1f", producto.distanciaKm)} km de ti"
                )
            }
        }
    }
}

@Composable
private fun Escribiendo() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.Space16, vertical = Dimens.Space12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Dimens.Space12))
            Text(
                "Escribiendo…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BarraEscritura(
    texto: String,
    onTextoChange: (String) -> Unit,
    onEnviar: () -> Unit,
    onMicrofono: () -> Unit,
    escuchando: Boolean,
    puedeEnviar: Boolean
) {
    // Sin fondo propio: el de la pantalla sigue continuo detrás de la caja (con la barra flotante debajo,
    // un Surface con elevación se veía como una franja de otro tono)
    Box {
        BotiTextField(
            value = texto,
            onValueChange = onTextoChange,
            label = if (escuchando) "Escuchando…" else "Escribe tu pregunta",
            singleLine = false,
            enabled = !escuchando,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onEnviar() }),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMicrofono,
                        modifier = Modifier.size(Dimens.MinTouchTarget)
                    ) {
                        Icon(
                            imageVector = if (escuchando) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (escuchando) {
                                "Detener dictado"
                            } else {
                                "Hablar con el asistente"
                            },
                            tint = if (escuchando) {
                                MaterialTheme.colorScheme.error
                            } else {
                                LocalContentColor.current
                            }
                        )
                    }
                    IconButton(
                        onClick = onEnviar,
                        enabled = puedeEnviar,
                        modifier = Modifier.size(Dimens.MinTouchTarget)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Enviar")
                    }
                }
            },
            modifier = Modifier.padding(horizontal = Dimens.Space12, vertical = Dimens.Space8)
        )
    }
}