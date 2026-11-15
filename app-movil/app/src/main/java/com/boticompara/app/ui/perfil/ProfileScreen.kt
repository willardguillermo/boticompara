package com.boticompara.app.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.ui.components.*
import com.boticompara.app.ui.theme.Dimens

@Composable
fun ProfileScreen(
    navController: NavController,
    vm: ProfileViewModel = viewModel(),
    cuentaVm: CuentaViewModel = viewModel()
) {
    var mostrarDialogo by remember { mutableStateOf(false) }

    LaunchedEffect(vm.sesionVencida, cuentaVm.cuentaEliminada) {
        if (vm.sesionVencida || cuentaVm.cuentaEliminada) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    val iniciales = vm.nombre.trim().split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { navController.popBackStack() }) { Text("← Volver") }
        }
        Spacer(Modifier.height(Dimens.Space8))

        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                iniciales,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(Dimens.Space12))
        Text(
            vm.nombre.ifBlank { "Mi perfil" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            vm.correo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.Space24))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Space20)) {
                Text(
                    "Datos personales",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(Dimens.Space12))
                OutlinedTextField(
                    value = vm.nombre, onValueChange = { vm.nombre = it },
                    label = { Text("Nombre") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space12))
                OutlinedTextField(
                    value = vm.telefono, onValueChange = { vm.telefono = it },
                    label = { Text("Teléfono") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space12))
                OutlinedTextField(
                    value = vm.direccion, onValueChange = { vm.direccion = it },
                    label = { Text("Dirección") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.Space16))

                Button(
                    onClick = { vm.guardar() },
                    enabled = !vm.isLoading,
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
                        Text("Guardar cambios", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        vm.mensaje?.let {
            Spacer(Modifier.height(Dimens.Space12))
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
                        Icons.Rounded.CheckCircle,
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
        }
        Spacer(Modifier.height(Dimens.Space16))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Space20)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoCircular(
                        icono = if (vm.notificacionesPrecio) Icons.Rounded.NotificationsActive
                        else Icons.Rounded.NotificationsOff
                    )
                    Spacer(Modifier.width(Dimens.Space12))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Alertas de baja de precio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Te avisamos cuando baje el precio de un producto de tus favoritos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(Dimens.Space8))
                    Switch(
                        checked = vm.notificacionesPrecio,
                        onCheckedChange = { vm.cambiarNotificaciones(it) },
                        enabled = !vm.guardandoNotificaciones
                    )
                }
                if (!vm.notificacionesPrecio) {
                    Spacer(Modifier.height(Dimens.Space8))
                    Text(
                        "Con las alertas apagadas no se crean avisos nuevos ni llegan correos. Las alertas que ya tienes se conservan.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                vm.mensajeNotificaciones?.let {
                    Spacer(Modifier.height(Dimens.Space8))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.Space16))

        OutlinedButton(
            onClick = {
                vm.cerrarSesion {
                    navController.navigate("login") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget + 2.dp)
        ) {
            Text("Cerrar sesión", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(Dimens.Space24))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(Dimens.Space20)) {
                Text(
                    "Zona de peligro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(Dimens.Space4))
                Text(
                    "Si eliminas tu cuenta se borrarán tus datos personales y no podrás recuperarla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(Dimens.Space12))
                Button(
                    onClick = { mostrarDialogo = true },
                    enabled = !cuentaVm.isLoading,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget + 2.dp)
                ) {
                    if (cuentaVm.isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("Eliminar mi cuenta", fontWeight = FontWeight.SemiBold)
                    }
                }
                cuentaVm.error?.let {
                    Spacer(Modifier.height(Dimens.Space8))
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Spacer(Modifier.height(Dimens.Space24))
    }

    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Text("Se borrarán tus datos personales y se cerrará tu sesión. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarDialogo = false
                        cuentaVm.eliminarCuenta()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Sí, eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) { Text("Cancelar") }
            }
        )
    }

    if (cuentaVm.sesionVencida) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Rounded.Alarm, contentDescription = null) },
            title = { Text("Tu sesión venció") },
            text = {
                Text("Tu cuenta no se eliminó. Inicia sesión otra vez para eliminar tu cuenta.")
            },
            confirmButton = {
                Button(onClick = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }) { Text("Iniciar sesión") }
            }
        )
    }
}
