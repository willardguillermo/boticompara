package com.boticompara.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
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
import com.boticompara.app.ui.perfil.CuentaViewModel
import com.boticompara.app.ui.theme.Dimens

@Composable
fun RestablecerScreen(
    navController: NavController,
    correoInicial: String,
    vm: CuentaViewModel = viewModel()
) {
    var correo by remember { mutableStateOf(correoInicial) }
    var codigo by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var verContrasena by remember { mutableStateOf(false) }

    val transformacion = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation()

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
            icono = if (vm.restablecido) Icons.Rounded.CheckCircle else Icons.Rounded.Key,
            tamano = 88.dp,
            fondo = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(Dimens.Space20))

        if (vm.restablecido) {
            Text(
                "¡Contraseña actualizada!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.Space8))
            Text(
                "Ya puedes iniciar sesión con tu nueva contraseña.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.Space28))
            Button(
                onClick = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget + 2.dp)
            ) {
                Text("Ir a iniciar sesión", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                "Restablece tu contraseña",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.Space8))
            Text(
                "Escribe el código de 6 dígitos que te llegó al correo y tu nueva contraseña.",
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
                        value = correo,
                        onValueChange = { correo = it },
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Rounded.Mail, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Dimens.Space12))

                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { nuevo ->
                            if (nuevo.length <= 6 && nuevo.all { it.isDigit() }) codigo = nuevo
                        },
                        label = { Text("Código de 6 dígitos") },
                        placeholder = { Text("000000") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        supportingText = { Text("${codigo.length}/6") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Dimens.Space4))

                    OutlinedTextField(
                        value = nueva,
                        onValueChange = { nueva = it },
                        label = { Text("Nueva contraseña") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = transformacion,
                        trailingIcon = {
                            TextButton(onClick = { verContrasena = !verContrasena }) {
                                Text(if (verContrasena) "Ocultar" else "Ver")
                            }
                        },
                        supportingText = {
                            Text(
                                if (nueva.isEmpty() || nueva.length >= 8) "Mínimo 8 caracteres"
                                else "Te faltan ${8 - nueva.length} caracteres"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Dimens.Space4))

                    OutlinedTextField(
                        value = confirmar,
                        onValueChange = { confirmar = it },
                        label = { Text("Repite la nueva contraseña") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = transformacion,
                        isError = confirmar.isNotEmpty() && confirmar != nueva,
                        supportingText = {
                            if (confirmar.isNotEmpty() && confirmar != nueva) Text("No coinciden")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Dimens.Space16))

                    Button(
                        onClick = { vm.restablecer(correo, codigo, nueva, confirmar) },
                        enabled = correo.isNotBlank() && codigo.isNotEmpty() &&
                                nueva.isNotEmpty() && confirmar.isNotEmpty() && !vm.isLoading,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget + 2.dp)
                    ) {
                        if (vm.isLoading) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Cambiar contraseña", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            vm.error?.let {
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
            TextButton(onClick = { navController.popBackStack() }) {
                Text("Pedir otro código")
            }
        }
        Spacer(Modifier.height(Dimens.Space24))
    }
}
