package com.boticompara.app.vistas

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

// Pestañas de la barra inferior
private data class DestinoInferior(
    val ruta: String,
    val etiqueta: String,
    val icono: ImageVector,
    val iconoSeleccionado: ImageVector
)

private val destinos = listOf(
    DestinoInferior("busqueda", "Buscar", Icons.Rounded.Search, Icons.Rounded.Search),
    DestinoInferior("favoritos", "Favoritos", Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
    DestinoInferior("alertas", "Alertas", Icons.Rounded.NotificationsNone, Icons.Rounded.Notifications),   // H59
    DestinoInferior("perfil", "Perfil", Icons.Outlined.Person, Icons.Rounded.Person)
)

// La barra solo se muestra en estas pantallas (no en login, registro, detalle, etc.)
val RUTAS_CON_BARRA = destinos.map { it.ruta }

// Barra de navegación inferior (Material 3). alertasNoLeidas es el número sobre el ícono de Alertas (H59)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraNavegacion(navController: NavController, rutaActual: String?, alertasNoLeidas: Int = 0) {
    NavigationBar {
        destinos.forEach { destino ->
            val seleccionado = rutaActual == destino.ruta
            NavigationBarItem(
                selected = seleccionado,
                onClick = {
                    if (!seleccionado) {
                        navController.navigate(destino.ruta) {
                            popUpTo("busqueda") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (destino.ruta == "alertas" && alertasNoLeidas > 0) {
                                Badge { Text(if (alertasNoLeidas > 99) "99+" else alertasNoLeidas.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (seleccionado) destino.iconoSeleccionado else destino.icono,
                            contentDescription = destino.etiqueta
                        )
                    }
                },
                label = { Text(destino.etiqueta) },
                alwaysShowLabel = true
            )
        }
    }
}