package com.boticompara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.boticompara.app.vistas.LoginScreen
import com.boticompara.app.vistas.ProfileScreen
import com.boticompara.app.vistas.RegisterScreen
import com.boticompara.app.vistas.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login")    { LoginScreen(navController) }      // H18
        composable("registro") { RegisterScreen(navController) }   // H15
        composable("busqueda") { SearchScreen(navController) }     // H22, H23, H24
        composable("perfil")   { ProfileScreen(navController) }    // H16
    }
}