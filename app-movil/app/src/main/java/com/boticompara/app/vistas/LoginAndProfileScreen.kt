package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BotiCompara", style = MaterialTheme.typography.headlineLarge)
        Text("Compara precios de medicamentos", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = correo, onValueChange = { correo = it },
            label = { Text("Correo") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = contrasena, onValueChange = { contrasena = it },
            label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                authViewModel.login(correo, contrasena) {
                    navController.navigate("busqueda") { popUpTo("login") { inclusive = true } }
                }
            },
            enabled = correo.isNotBlank() && contrasena.isNotBlank() && !authViewModel.isLoading
        ) {
            if (authViewModel.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Iniciar sesión")
        }

        authViewModel.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = { navController.navigate("registro") }) {
            Text("¿No tienes cuenta? Regístrate")
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
fun ProfileScreen(navController: NavController, vm: ProfileViewModel = viewModel()) {
    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) navController.navigate("login") { popUpTo(0) }
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
            vm.cerrarSesion { navController.navigate("login") { popUpTo(0) } }
        }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
    }
}