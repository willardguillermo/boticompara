package com.boticompara.app.vistas

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
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
    DestinoInferior("perfil", "Perfil", Icons.Outlined.Person, Icons.Rounded.Person)
)

// La barra solo se muestra en estas pantallas (no en login, registro, detalle, etc.)
val RUTAS_CON_BARRA = destinos.map { it.ruta }

// Barra de navegación inferior (Material 3)
@Composable
fun BarraNavegacion(navController: NavController, rutaActual: String?) {
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
                    Icon(
                        imageVector = if (seleccionado) destino.iconoSeleccionado else destino.icono,
                        contentDescription = destino.etiqueta
                    )
                },
                label = { Text(destino.etiqueta) },
                alwaysShowLabel = true
            )
        }
    }
}