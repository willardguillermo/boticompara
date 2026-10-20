package com.boticompara.app.vistas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

// ===================== RESTABLECER CONTRASEÑA CON CÓDIGO (H17) =====================
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
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (vm.restablecido) "✅" else "🔑", fontSize = 40.sp)
        }
        Spacer(Modifier.height(20.dp))

        if (vm.restablecido) {
            // ---------- Éxito ----------
            Text(
                "¡Contraseña actualizada!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ya puedes iniciar sesión con tu nueva contraseña.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Ir a iniciar sesión", fontWeight = FontWeight.SemiBold)
            }
        } else {
            // ---------- Formulario ----------
            Text(
                "Restablece tu contraseña",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Escribe el código de 6 dígitos que te llegó al correo y tu nueva contraseña.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = correo,
                        onValueChange = { correo = it },
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    // Solo acepta números y máximo 6
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { nuevo ->
                            if (nuevo.length <= 6 && nuevo.all { it.isDigit() }) codigo = nuevo
                        },
                        label = { Text("Código de 6 dígitos") },
                        placeholder = { Text("000000") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        supportingText = { Text("${codigo.length}/6") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = nueva,
                        onValueChange = { nueva = it },
                        label = { Text("Nueva contraseña") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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
                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = confirmar,
                        onValueChange = { confirmar = it },
                        label = { Text("Repite la nueva contraseña") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = transformacion,
                        isError = confirmar.isNotEmpty() && confirmar != nueva,
                        supportingText = {
                            if (confirmar.isNotEmpty() && confirmar != nueva) Text("No coinciden")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { vm.restablecer(correo, codigo, nueva, confirmar) },
                        enabled = correo.isNotBlank() && codigo.isNotEmpty() &&
                                nueva.isNotEmpty() && confirmar.isNotEmpty() && !vm.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
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
            TextButton(onClick = { navController.popBackStack() }) {
                Text("← Pedir otro código")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}