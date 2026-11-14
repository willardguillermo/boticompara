package com.boticompara.app.ui.navigation

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
import com.boticompara.app.ui.perfil.ProfileScreen

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
        modifier = modifier
    ) {
        composable("login") { LoginScreen(navController) }
        composable("registro") { RegisterScreen(navController) }
        composable("recuperar") { RecuperarScreen(navController) }   // H17
        composable("restablecer/{correo}") { entry ->                 // H17
            RestablecerScreen(navController, entry.arguments?.getString("correo") ?: "")
        }
        composable("verificar/{correo}") { entry ->                   // H20
            VerificarCorreoScreen(navController, entry.arguments?.getString("correo") ?: "")
        }
        composable("busqueda") { SearchScreen(navController) }
        composable("favoritos") { FavoritosScreen(navController) }    // H44, H45
        composable("alertas") { AlertasScreen(navController, alertasViewModel) }   // H59
        composable("perfil") { ProfileScreen(navController) }

        // H28: Ruta para el detalle del producto por ID
        composable(
            route = "detalle/{productoId}",
            arguments = listOf(navArgument("productoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val productoId = backStackEntry.arguments?.getLong("productoId") ?: 0L
            ProductoDetalleScreen(productoId = productoId, navController = navController)
        }
    }
}
