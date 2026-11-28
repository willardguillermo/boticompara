package com.boticompara.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.boticompara.app.ui.navigation.DESENFOQUE_DISPONIBLE
import com.boticompara.app.ui.navigation.LocalEspacioBarra
import com.boticompara.app.ui.navigation.LocalPausasDesenfoque
import com.boticompara.app.ui.navigation.espacioBarra
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.ui.alertas.AlertasViewModel
import com.boticompara.app.ui.navigation.AppNav
import com.boticompara.app.ui.navigation.BarraNavegacion
import com.boticompara.app.ui.navigation.RUTAS_CON_BARRA
import com.boticompara.app.ui.theme.BotiComparaAppTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenManager = TokenManager(applicationContext)

        setContent {
            BotiComparaAppTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }

                // Android 17 (API 37) bloquea la red local (10.0.2.2) sin este permiso.
                // Si el usuario lo rechaza, la app abre igual.
                val permisoRedLocal = "android.permission.ACCESS_LOCAL_NETWORK"
                val pedirPermiso = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 37 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, permisoRedLocal)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        pedirPermiso.launch(permisoRedLocal)
                    }
                }

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
                    val entradaActual by navController.currentBackStackEntryAsState()
                    val rutaActual = entradaActual?.destination?.route
                    val mostrarBarra = rutaActual in RUTAS_CON_BARRA

                    // H59: un solo ViewModel para el número del ícono y la pantalla de Alertas
                    val alertasViewModel: AlertasViewModel = viewModel()
                    LaunchedEffect(rutaActual) {
                        if (rutaActual == "login") alertasViewModel.limpiar()
                        else if (rutaActual in RUTAS_CON_BARRA) alertasViewModel.actualizarResumen()
                    }

                    // La barra flota sobre el contenido (H20, vidrio): el contenido llega hasta abajo y cada
                    // pantalla con barra suma LocalEspacioBarra a su padding inferior para no quedar tapada.
                    val espacio = if (mostrarBarra) espacioBarra() else 0.dp
                    // Android 12+: la pantalla se graba en esta capa y la barra la dibuja desenfocada
                    val capaFondo = rememberGraphicsLayer()
                    val pausasDesenfoque = remember { mutableIntStateOf(0) }
                    val grabarFondo = mostrarBarra && DESENFOQUE_DISPONIBLE && pausasDesenfoque.intValue == 0

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        CompositionLocalProvider(
                            LocalEspacioBarra provides espacio,
                            LocalPausasDesenfoque provides pausasDesenfoque
                        ) {
                            AppNav(
                                navController = navController,
                                startDestination = startDestination!!,
                                alertasViewModel = alertasViewModel,
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Los Scaffold de las pantallas ya no suman la barra del sistema por abajo
                                    .consumeWindowInsets(PaddingValues(bottom = espacio))
                                    .then(
                                        if (grabarFondo) {
                                            Modifier.drawWithContent {
                                                capaFondo.record { this@drawWithContent.drawContent() }
                                                drawLayer(capaFondo)
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                        if (mostrarBarra) {
                            BarraNavegacion(
                                navController = navController,
                                rutaActual = rutaActual,
                                alertasNoLeidas = alertasViewModel.noLeidas,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                fondo = if (grabarFondo) capaFondo else null
                            )
                        }
                    }
                }
            }
        }
    }
}
