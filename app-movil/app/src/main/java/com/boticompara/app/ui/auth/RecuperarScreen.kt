package com.boticompara.app.ui.auth

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.perfil.CuentaViewModel
import com.boticompara.app.ui.theme.Dimens

@Composable
fun RecuperarScreen(navController: NavController, vm: CuentaViewModel = viewModel()) {
    var correo by remember { mutableStateOf("") }

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
            icono = Icons.Rounded.LockReset,
            tamano = 88.dp,
            fondo = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(Dimens.Space20))

        Text(
            "¿Olvidaste tu contraseña?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.Space8))
        Text(
            "No te preocupes. Escribe el correo de tu cuenta y te enviaremos un código de 6 dígitos para restablecerla.",
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
                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo electrónico") },
                    placeholder = { Text("tucorreo@ejemplo.com") },
                    leadingIcon = { Icon(Icons.Rounded.Mail, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    isError = vm.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space16))

                Button(
                    onClick = { vm.solicitarCodigo(correo) },
                    enabled = correo.isNotBlank() && !vm.isLoading,
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
                        Text("Enviar código", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        vm.mensaje?.let {
            Spacer(Modifier.height(Dimens.Space16))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Dimens.Space16),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.MarkEmailRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(Dimens.Space12))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Space12))
            OutlinedButton(
                onClick = { navController.navigate("restablecer/${Uri.encode(correo.trim())}") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget + 2.dp)
            ) {
                Text("Ya tengo un código", fontWeight = FontWeight.SemiBold)
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
            Text("Volver al inicio de sesión")
        }
    }
}
