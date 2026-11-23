package com.boticompara.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.boticompara.app.ui.alertas.AlertasScreen
import com.boticompara.app.ui.alertas.AlertasViewModel
import com.boticompara.app.ui.auth.LoginScreen
import com.boticompara.app.ui.auth.RecuperarScreen
import com.boticompara.app.ui.auth.RegisterScreen
import com.boticompara.app.ui.auth.RestablecerScreen
import com.boticompara.app.ui.auth.VerificarCorreoScreen
import com.boticompara.app.ui.busqueda.SearchScreen
import com.boticompara.app.ui.detalle.ProductoDetalleScreen
import com.boticompara.app.ui.favoritos.FavoritosScreen
import com.boticompara.app.ui.reservas.MisReservasScreen
import com.boticompara.app.ui.reservas.RUTA_RESERVA
import com.boticompara.app.ui.reservas.ReservaDetalleScreen
import com.boticompara.app.ui.resenas.RUTA_RESENAS
import com.boticompara.app.ui.resenas.ResenasBoticaScreen
import com.boticompara.app.ui.perfil.ProfileScreen
import com.boticompara.app.ui.chat.ChatScreen

@Composable
fun AppNav(
    navController: NavHostController,
    startDestination: String,
    alertasViewModel: AlertasViewModel,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { it / 4 } },
        exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { -it / 4 } },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { -it / 4 } },
        popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { it / 4 } }
    ) {
        composable("login") { LoginScreen(navController) }
        composable("registro") { RegisterScreen(navController) }
        composable("recuperar") { RecuperarScreen(navController) }
        composable("restablecer/{correo}") { entry ->
            RestablecerScreen(navController, entry.arguments?.getString("correo") ?: "")
        }
        composable("verificar/{correo}") { entry ->
            VerificarCorreoScreen(navController, entry.arguments?.getString("correo") ?: "")
        }
        composable("busqueda") { SearchScreen(navController) }
        composable("favoritos") { FavoritosScreen(navController) }
        composable("alertas") { AlertasScreen(navController, alertasViewModel) }
        composable("perfil") { ProfileScreen(navController) }
        composable("chat") { ChatScreen(navController) }

        composable(
            route = "detalle/{productoId}",
            arguments = listOf(navArgument("productoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val productoId = backStackEntry.arguments?.getLong("productoId") ?: 0L
            ProductoDetalleScreen(productoId = productoId, navController = navController)
        }

        // H76: historial de reservas; H73, H75, H76: detalle (nueva = true al acabar de crearla)
        composable("reservas") { MisReservasScreen(navController) }
        composable(
            route = RUTA_RESERVA,
            arguments = listOf(
                navArgument("reservaId") { type = NavType.LongType },
                navArgument("nueva") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            ReservaDetalleScreen(
                reservaId = backStackEntry.arguments?.getLong("reservaId") ?: 0L,
                nueva = backStackEntry.arguments?.getBoolean("nueva") ?: false,
                navController = navController
            )
        }

        composable(
            route = RUTA_RESENAS,
            arguments = listOf(navArgument("boticaId") { type = NavType.LongType })
        ) { backStackEntry ->
            ResenasBoticaScreen(
                boticaId = backStackEntry.arguments?.getLong("boticaId") ?: 0L,
                navController = navController
            )
        }
    }
}
