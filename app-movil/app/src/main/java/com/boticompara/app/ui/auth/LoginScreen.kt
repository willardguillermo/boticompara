package com.boticompara.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.BotiTonalButton
import com.boticompara.app.ui.components.BotiTextField
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.components.SeccionTitulo
import com.boticompara.app.ui.theme.Dimens

@Composable
fun LoginScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var verContrasena by remember { mutableStateOf(false) }
    val contexto = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.Space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconoCircular(
            icono = Icons.Rounded.LocalPharmacy,
            tamano = 96.dp,
            fondo = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(Dimens.Space16))

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
        Spacer(Modifier.height(Dimens.Space28))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Space20)) {
                SeccionTitulo(titulo = "Inicia sesión")
                Spacer(Modifier.height(Dimens.Space12))

                BotiTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = "Correo electrónico",
                    leadingIcon = Icons.Rounded.Mail,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(Dimens.Space12))

                BotiTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    label = "Contraseña",
                    leadingIcon = Icons.Rounded.Lock,
                    singleLine = true,
                    visualTransformation = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { verContrasena = !verContrasena }) {
                            Text(if (verContrasena) "Ocultar" else "Ver")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                TextButton(
                    onClick = { navController.navigate("recuperar") },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("¿Olvidaste tu contraseña?")
                }
                Spacer(Modifier.height(Dimens.Space8))

                BotiButton(
                    text = if (authViewModel.isLoading) "Iniciando..." else "Iniciar sesión",
                    onClick = {
                        authViewModel.login(correo, contrasena) {
                            navController.navigate("busqueda") { popUpTo("login") { inclusive = true } }
                        }
                    },
                    enabled = correo.isNotBlank() && contrasena.isNotBlank() && !authViewModel.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                // H67: iniciar sesión con Google (solo compradores)
                Spacer(Modifier.height(Dimens.Space12))
                Text(
                    "o",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space12))
                BotiTonalButton(
                    text = "Continuar con Google",
                    icon = Icons.Rounded.AccountCircle,
                    onClick = {
                        authViewModel.loginConGoogle(contexto) {
                            navController.navigate("busqueda") { popUpTo("login") { inclusive = true } }
                        }
                    },
                    enabled = !authViewModel.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
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
            Text("¿No tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { navController.navigate("registro") }) {
                Text("Regístrate", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
