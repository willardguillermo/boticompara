package com.boticompara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.ui.theme.BotiComparaAppTheme
import com.boticompara.app.vistas.LoginScreen
import com.boticompara.app.vistas.ProfileScreen
import com.boticompara.app.vistas.RegisterScreen
import com.boticompara.app.vistas.SearchScreen
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenManager = TokenManager(applicationContext)

        setContent {
            BotiComparaAppTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }

                // Comprobar si ya existe token guardado (H18)
                LaunchedEffect(Unit) {
                    val token = tokenManager.tokenFlow.first()
                    startDestination = if (!token.isNullOrBlank()) "busqueda" else "login"
                }

                if (startDestination == null) {
                    // Pantalla de carga mientras se verifica la sesión
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = startDestination!!
                    ) {
                        composable("login") { LoginScreen(navController) }
                        composable("registro") { RegisterScreen(navController) }
                        composable("busqueda") { SearchScreen(navController) }
                        composable("perfil") { ProfileScreen(navController) }
                    }
                }
            }
        }
    }
}