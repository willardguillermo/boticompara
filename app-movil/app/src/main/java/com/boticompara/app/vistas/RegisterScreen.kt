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
    var telefono by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }

    val correoValido = android.util.Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()
    val formularioValido = nombre.isNotBlank() && correoValido && contrasena.length >= 8

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Registro de comprador", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nombre, onValueChange = { nombre = it },
            label = { Text("Nombre") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = correo, onValueChange = { correo = it },
            label = { Text("Correo") }, singleLine = true,
            isError = correo.isNotEmpty() && !correoValido,
            supportingText = { if (correo.isNotEmpty() && !correoValido) Text("Correo no válido") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        OutlinedTextField(
            value = telefono, onValueChange = { telefono = it },
            label = { Text("Teléfono (opcional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = contrasena, onValueChange = { contrasena = it },
            label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = contrasena.isNotEmpty() && contrasena.length < 8,
            supportingText = { Text("Mínimo 8 caracteres") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(8.dp))

        // H21: no se solicitan datos de salud
        Text(
            "BotiCompara no te pide datos de salud ni historial clínico.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                authViewModel.registrar(nombre, correo, contrasena, telefono.ifBlank { null }) {
                    navController.navigate("busqueda") { popUpTo("login") { inclusive = true } }
                }
            },
            enabled = formularioValido && !authViewModel.isLoading
        ) {
            if (authViewModel.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Registrarse")
        }

        authViewModel.errorMessage?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = { navController.popBackStack() }) {
            Text("Volver al login")
        }
    }
}