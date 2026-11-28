package com.boticompara.app.ui.navigation

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.boticompara.app.ui.components.animacionesApagadas

private data class DestinoInferior(
    val ruta: String,
    val etiqueta: String,
    val icono: ImageVector,
    val iconoSeleccionado: ImageVector
)

private val destinos = listOf(
    DestinoInferior("busqueda", "Buscar", Icons.Outlined.Search, Icons.Rounded.Search),
    DestinoInferior("favoritos", "Favoritos", Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
    DestinoInferior("alertas", "Alertas", Icons.Rounded.NotificationsNone, Icons.Rounded.Notifications),
    DestinoInferior("chat", "Asistente", Icons.Rounded.ChatBubbleOutline, Icons.AutoMirrored.Rounded.Chat),
    DestinoInferior("perfil", "Perfil", Icons.Outlined.Person, Icons.Rounded.Person)
)

val RUTAS_CON_BARRA = destinos.map { it.ruta }

/** Alto de la píldora y su separación del borde inferior (sin contar la barra del sistema). */
val ALTO_BARRA = 64.dp
val MARGEN_BARRA = 12.dp

/**
 * Lo que tapa la barra flotante desde abajo (barra + márgenes + barra del sistema), o 0 si no hay barra.
 * Las pantallas con barra lo suman al padding inferior de su contenido para que nada quede tapado.
 */
val LocalEspacioBarra = compositionLocalOf { 0.dp }

/** El espacio inferior que ocupa la barra con los insets del sistema actuales. */
@Composable
fun espacioBarra(): Dp =
    ALTO_BARRA + MARGEN_BARRA * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Cuántas pantallas visibles piden apagar el desenfoque. El mapa de Google se dibuja en un SurfaceView,
 * que deja de verse si la pantalla se graba en una capa; mientras el mapa está visible, la barra queda
 * solo translúcida. Es un contador para que dos pantallas a la vez (en una transición) no se pisen.
 */
val LocalPausasDesenfoque = compositionLocalOf<MutableIntState> { mutableIntStateOf(0) }

/** Apaga el desenfoque de la barra mientras este composable esté en pantalla (ver [LocalPausasDesenfoque]). */
@Composable
fun PausarDesenfoqueBarra() {
    val pausas = LocalPausasDesenfoque.current
    DisposableEffect(pausas) {
        pausas.intValue++
        onDispose { pausas.intValue-- }
    }
}

/** Android 12+ (API 31) tiene RenderEffect: ahí la barra desenfoca lo que pasa por detrás. */
val DESENFOQUE_DISPONIBLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Barra de navegación de vidrio: una píldora flotante, separada de los bordes, translúcida y con un borde
 * sutil. En Android 12+ dibuja desenfocado el contenido que tiene detrás ([fondo], la pantalla grabada en
 * un GraphicsLayer); en 11 o menos, o sin [fondo], es solo translúcida (más opaca para que se lea igual).
 * El indicador de la pestaña activa se desliza; con la escala de animación en 0 cambia sin moverse.
 * TalkBack lee cada pestaña con su nombre, si está seleccionada y su posición (rol Tab).
 */
@Composable
fun BarraNavegacion(
    navController: NavController,
    rutaActual: String?,
    modifier: Modifier = Modifier,
    alertasNoLeidas: Int = 0,
    fondo: GraphicsLayer? = null
) {
    val oscuro = isSystemInDarkTheme()
    val conDesenfoque = fondo != null && DESENFOQUE_DISPONIBLE
    val forma = RoundedCornerShape(50)
    val tinte = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (conDesenfoque) 0.72f else 0.94f)
    val borde = if (oscuro) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f)))
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.9f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        )
    }
    var posicion by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = MARGEN_BARRA)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTO_BARRA)
                .onGloballyPositioned { posicion = it.positionInRoot() }
                .clip(forma)
        ) {
            if (conDesenfoque && fondo != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            renderEffect = BlurEffect(28.dp.toPx(), 28.dp.toPx(), TileMode.Clamp)
                            clip = true
                        }
                        .drawBehind {
                            // El contenido de la pantalla, movido para que coincida con lo que hay detrás
                            translate(-posicion.x, -posicion.y) { drawLayer(fondo) }
                        }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tinte)
                    .border(1.dp, borde, forma)
            )
            ContenidoBarra(navController, rutaActual, alertasNoLeidas)
        }
    }
}

@Composable
private fun ContenidoBarra(navController: NavController, rutaActual: String?, alertasNoLeidas: Int) {
    val indice = destinos.indexOfFirst { it.ruta == rutaActual }
    val sinAnimacion = animacionesApagadas()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        val anchoPestana = maxWidth / destinos.size
        val desplazamiento by animateDpAsState(
            targetValue = anchoPestana * indice.coerceAtLeast(0),
            animationSpec = if (sinAnimacion) snap() else spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "indicador"
        )
        if (indice >= 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(desplazamiento.roundToPx(), 0) }
                    .width(anchoPestana)
                    .fillMaxHeight()
                    .padding(vertical = 7.dp, horizontal = 3.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(50))
            )
        }
        Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
            destinos.forEach { destino ->
                val seleccionado = rutaActual == destino.ruta
                Pestana(
                    destino = destino,
                    seleccionado = seleccionado,
                    noLeidas = if (destino.ruta == "alertas") alertasNoLeidas else 0,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!seleccionado) {
                            navController.navigate(destino.ruta) {
                                popUpTo("busqueda") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Pestana(
    destino: DestinoInferior,
    seleccionado: Boolean,
    noLeidas: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val color = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .selectable(selected = seleccionado, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgedBox(
            badge = {
                if (noLeidas > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            if (noLeidas > 99) "99+" else noLeidas.toString(),
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = if (noLeidas == 1) "1 sin leer" else "$noLeidas sin leer"
                            }
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (seleccionado) destino.iconoSeleccionado else destino.icono,
                contentDescription = null,   // la etiqueta de abajo ya dice el nombre
                tint = color
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            destino.etiqueta,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
