package com.boticompara.app.vistas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var errorTelefono by remember { mutableStateOf<String?>(null) }

    // Validar teléfono: opcional, pero si se escribe debe tener entre 6 y 15 dígitos
    fun esTelefonoValido(num: String): Boolean {
        val limpio = num.trim()
        if (limpio.isEmpty()) return true
        return limpio.matches(Regex("^[0-9]{6,15}$"))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Registro de Comprador", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nombre, onValueChange = { nombre = it },
            label = { Text("Nombre completo *") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = correo, onValueChange = { correo = it },
            label = { Text("Correo electrónico *") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = contrasena, onValueChange = { contrasena = it },
            label = { Text("Contraseña *") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = telefono,
            onValueChange = { input ->
                telefono = input
                errorTelefono = if (esTelefonoValido(input)) null else "El teléfono debe tener entre 6 y 15 dígitos"
            },
            label = { Text("Teléfono (opcional)") }, singleLine = true,
            isError = errorTelefono != null,
            supportingText = { errorTelefono?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (esTelefonoValido(telefono)) {
                    authViewModel.registrar(
                        nombre = nombre,
                        correo = correo,
                        contrasena = contrasena,
                        telefono = telefono.ifBlank { null }
                    ) {
                        navController.navigate("busqueda") { popUpTo("login") { inclusive = true } }
                    }
                }
            },
            enabled = nombre.isNotBlank() && correo.isNotBlank() && contrasena.isNotBlank() && errorTelefono == null && !authViewModel.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (authViewModel.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Registrarse")
        }

        authViewModel.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = { navController.popBackStack() }) {
            Text("¿Ya tienes cuenta? Inicia sesión")
        }
    }
}