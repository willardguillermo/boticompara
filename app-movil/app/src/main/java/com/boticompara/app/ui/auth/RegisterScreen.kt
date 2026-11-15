package com.boticompara.app.ui.auth

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.theme.Dimens

@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var errorTelefono by remember { mutableStateOf<String?>(null) }
    var verContrasena by remember { mutableStateOf(false) }

    fun esTelefonoValido(num: String): Boolean {
        val limpio = num.trim()
        if (limpio.isEmpty()) return true
        return limpio.matches(Regex("^[0-9]{6,15}$"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.Space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Dimens.Space24))
        IconoCircular(
            icono = Icons.Rounded.Person,
            tamano = 88.dp,
            fondo = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(Dimens.Space16))

        Text(
            "Crea tu cuenta",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Regístrate gratis y empieza a comparar precios",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.Space24))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Space20)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre completo *") },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space12))

                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo electrónico *") },
                    placeholder = { Text("tucorreo@ejemplo.com") },
                    leadingIcon = { Icon(Icons.Rounded.Mail, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space12))

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    label = { Text("Contraseña *") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = if (verContrasena) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { verContrasena = !verContrasena }) {
                            Text(if (verContrasena) "Ocultar" else "Ver")
                        }
                    },
                    supportingText = {
                        Text(
                            if (contrasena.isEmpty() || contrasena.length >= 8) "Mínimo 8 caracteres"
                            else "Te faltan ${8 - contrasena.length} caracteres"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space4))

                OutlinedTextField(
                    value = telefono,
                    onValueChange = { input ->
                        telefono = input
                        errorTelefono = if (esTelefonoValido(input)) null
                        else "El teléfono debe tener entre 6 y 15 dígitos"
                    },
                    label = { Text("Teléfono (opcional)") },
                    leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    isError = errorTelefono != null,
                    supportingText = { errorTelefono?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space8))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Dimens.Space8))
                    Text(
                        "BotiCompara no te pide datos de salud ni historial clínico.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(Dimens.Space16))

                Button(
                    onClick = {
                        if (esTelefonoValido(telefono)) {
                            authViewModel.registrar(
                                nombre = nombre,
                                correo = correo,
                                contrasena = contrasena,
                                telefono = telefono.ifBlank { null }
                            ) {
                                navController.navigate("verificar/${Uri.encode(correo.trim())}") {
                                    popUpTo("registro") { inclusive = true }
                                }
                            }
                        }
                    },
                    enabled = nombre.isNotBlank() && correo.isNotBlank() && contrasena.isNotBlank() &&
                            errorTelefono == null && !authViewModel.isLoading,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget + 2.dp)
                ) {
                    if (authViewModel.isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Registrarse", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        authViewModel.errorMessage?.let {
            Spacer(Modifier.height(Dimens.Space16))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Dimens.Space16)
                )
            }
        }

        Spacer(Modifier.height(Dimens.Space16))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿Ya tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { navController.popBackStack() }) {
                Text("Inicia sesión", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(Dimens.Space24))
    }
}
