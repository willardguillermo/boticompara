package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.local.TokenManager
import com.boticompara.app.data.remote.PerfilRequest
import com.boticompara.app.data.remote.RetrofitClient
import kotlinx.coroutines.launch

// ===================== LOGIN (H18) =====================
@Composable
fun LoginScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var verContrasena by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo circular
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("💊", fontSize = 44.sp)
        }
        Spacer(Modifier.height(16.dp))

        Text(
            "BotiCompara",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Compara precios de medicamentos y ahorra",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        // Tarjeta con el formulario
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Inicia sesión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo electrónico") },
                    placeholder = { Text("tucorreo@ejemplo.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (verContrasena) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { verContrasena = !verContrasena }) {
                            Text(if (verContrasena) "Ocultar" else "Ver")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                // H17: recuperar contraseña
                TextButton(
                    onClick = { navController.navigate("recuperar") },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("¿Olvidaste tu contraseña?")
                }

                Button(
                    onClick = {
                        authViewModel.login(correo, contrasena) {
                            navController.navigate("busqueda") { popUpTo("login") { inclusive = true } }
                        }
                    },
                    enabled = correo.isNotBlank() && contrasena.isNotBlank() && !authViewModel.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (authViewModel.isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Iniciar sesión", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Mensaje de error
        authViewModel.errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "⚠️  $it",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿No tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { navController.navigate("registro") }) {
                Text("Regístrate", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ===================== PERFIL (H16) =====================
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.getInstance(application)
    private val tokenManager = TokenManager(application)

    var nombre by mutableStateOf("")
    var telefono by mutableStateOf("")
    var direccion by mutableStateOf("")
    var correo by mutableStateOf("")
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    init { cargarPerfil() }

    fun cargarPerfil() {
        viewModelScope.launch {
            isLoading = true
            try {
                val resp = api.obtenerPerfil(tokenManager.getBearer())
                val p = resp.body()
                if (resp.isSuccessful && p != null) {
                    nombre = p.nombre
                    correo = p.correo
                    telefono = p.telefono ?: ""
                    direccion = p.direccion ?: ""
                } else if (resp.code() == 401) {
                    tokenManager.clearSession(); sesionVencida = true
                }
            } catch (e: Exception) {
                mensaje = "No se pudo conectar con el servidor"
            } finally { isLoading = false }
        }
    }

    fun guardar() {
        if (nombre.isBlank()) { mensaje = "El nombre es obligatorio"; return }
        viewModelScope.launch {
            isLoading = true
            try {
                val resp = api.actualizarPerfil(
                    tokenManager.getBearer(),
                    PerfilRequest(nombre.trim(), telefono.ifBlank { null }, direccion.ifBlank { null })
                )
                mensaje = when {
                    resp.isSuccessful -> "¡Cambios guardados correctamente!"
                    resp.code() == 401 -> { tokenManager.clearSession(); sesionVencida = true; null }
                    else -> "No se pudieron guardar los cambios"
                }
            } catch (e: Exception) {
                mensaje = "No se pudo conectar con el servidor"
            } finally { isLoading = false }
        }
    }

    fun cerrarSesion(onDone: () -> Unit) {
        viewModelScope.launch { tokenManager.clearSession(); onDone() }
    }
}

@Composable
fun ProfileScreen(
    navController: NavController,
    vm: ProfileViewModel = viewModel(),
    cuentaVm: CuentaViewModel = viewModel()   // H19
) {
    var mostrarDialogo by remember { mutableStateOf(false) }

    // Si el token venció o la cuenta se eliminó, volver al login limpiando la navegación
    LaunchedEffect(vm.sesionVencida, cuentaVm.cuentaEliminada) {
        if (vm.sesionVencida || cuentaVm.cuentaEliminada) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mi perfil", style = MaterialTheme.typography.headlineMedium)
        Text(vm.correo, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(value = vm.nombre, onValueChange = { vm.nombre = it },
            label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = vm.telefono, onValueChange = { vm.telefono = it },
            label = { Text("Teléfono") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = vm.direccion, onValueChange = { vm.direccion = it },
            label = { Text("Dirección") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))

        Button(onClick = { vm.guardar() }, enabled = !vm.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar cambios")
        }
        vm.mensaje?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { navController.popBackStack() }) { Text("Volver a la búsqueda") }
        TextButton(onClick = {
            vm.cerrarSesion {
                navController.navigate("login") {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }

        // ---------- H19: eliminar cuenta ----------
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { mostrarDialogo = true },
            enabled = !cuentaVm.isLoading,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (cuentaVm.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Eliminar mi cuenta")
        }
        cuentaVm.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    // Diálogo de confirmación (H19)
    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Text("Se borrarán tus datos personales y se cerrará tu sesión. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    mostrarDialogo = false
                    cuentaVm.eliminarCuenta()
                }) {
                    Text("Sí, eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) { Text("Cancelar") }
            }
        )
    }
}