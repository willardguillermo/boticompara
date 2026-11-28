package com.boticompara.app.ui.auth

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.ui.auth.codigo.TarjetaCodigo
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.BotiTonalButton
import com.boticompara.app.ui.components.BotiTextField
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.components.SeccionTitulo
import com.boticompara.app.ui.theme.Dimens
import com.boticompara.app.ui.theme.LocalExtendedColors
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun LoginScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var verContrasena by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val entrar = {
        navController.navigate("busqueda") { popUpTo("login") { inclusive = true } }
    }

    // H17 y H20: al volver de la recuperación o del registro llegan el aviso y el correo (nunca la contraseña)
    val estadoGuardado = navController.currentBackStackEntry?.savedStateHandle
    val aviso by (estadoGuardado?.getStateFlow<String?>(CLAVE_AVISO_LOGIN, null) ?: MutableStateFlow(null))
        .collectAsState()
    LaunchedEffect(estadoGuardado) {
        estadoGuardado?.remove<String>(CLAVE_CORREO_LOGIN)?.takeIf { it.isNotBlank() }?.let { correo = it }
    }

    // H20: la cuenta no tiene el correo verificado (403 CORREO_NO_VERIFICADO): se pide el código aquí mismo
    val verificacion = authViewModel.verificacion
    if (verificacion != null) {
        BackHandler(enabled = !verificacion.ocupado) { authViewModel.cancelarVerificacion() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.Space24),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TarjetaCodigo(
                estado = verificacion,
                titulo = authViewModel.tituloVerificacion,
                subtitulo = buildAnnotatedString {
                    append("Tu cuenta es anterior a la verificación por código. Te enviamos uno a ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(authViewModel.correoVerificacion) }
                    append("; vence en 15 minutos.")
                },
                subtituloExito = "Listo. Entrando a BotiCompara…",
                onCambio = { authViewModel.cambiarCodigo(it, entrar) },
                onVerificar = { authViewModel.confirmarCodigo(entrar) },
                onReenviar = authViewModel::reenviarCodigo,
                textoBoton = "Verificar y entrar",
                textoCambiar = "Volver al inicio de sesión",
                onCambiar = authViewModel::cancelarVerificacion
            )
        }
        return
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
                        estadoGuardado?.remove<String>(CLAVE_AVISO_LOGIN)
                        authViewModel.login(correo, contrasena, entrar)
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
                    onClick = { authViewModel.loginConGoogle(contexto, entrar) },
                    enabled = !authViewModel.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (aviso != null && authViewModel.errorMessage == null) {
            Spacer(Modifier.height(Dimens.Space16))
            Surface(
                color = LocalExtendedColors.current.exitoContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    aviso.orEmpty(),
                    color = LocalExtendedColors.current.onExitoContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Dimens.Space16)
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
