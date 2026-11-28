package com.boticompara.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.ui.auth.codigo.TarjetaCodigo
import com.boticompara.app.ui.auth.codigo.formatoReloj
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.BotiTextField
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.components.TarjetaVidrio
import com.boticompara.app.ui.theme.Dimens

/** Claves del savedStateHandle del login para mostrar "Contraseña actualizada" al volver. */
const val CLAVE_AVISO_LOGIN = "aviso"
private const val AVISO_PASSWORD_ACTUALIZADA = "Contraseña actualizada. Ingresa con tu nueva contraseña."

// H17 - Recuperar la contraseña con un código: correo → código + contraseña nueva (ver RecuperarViewModel)
@Composable
fun RecuperarScreen(
    navController: NavController,
    vm: RecuperarViewModel = viewModel(factory = RecuperarViewModel.Factory)
) {
    fun volverAlLogin(conAviso: Boolean) {
        navController.previousBackStackEntry?.savedStateHandle?.let { estado ->
            if (conAviso) {
                estado[CLAVE_AVISO_LOGIN] = AVISO_PASSWORD_ACTUALIZADA
                estado[CLAVE_CORREO_LOGIN] = vm.correoParaLogin
            }
        }
        if (!navController.popBackStack("login", inclusive = false)) navController.navigate("login")
    }

    LaunchedEffect(vm.restablecido) {
        if (vm.restablecido) volverAlLogin(conAviso = true)
    }
    BackHandler(enabled = vm.paso == PasoRecuperar.CODIGO && !vm.codigo.ocupado) { vm.usarOtroCorreo() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Dimens.Space16))
        AnimatedContent(
            targetState = vm.paso,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "paso"
        ) { paso ->
            when (paso) {
                PasoRecuperar.CORREO -> PasoCorreoRecuperar(vm)
                PasoRecuperar.CODIGO -> Column {
                    vm.mensajeServidor?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(Dimens.Space12)
                            )
                        }
                        Spacer(Modifier.height(Dimens.Space16))
                    }
                    TarjetaCodigo(
                        estado = vm.codigo,
                        titulo = "Crea tu nueva contraseña",
                        tituloExito = "Contraseña actualizada",
                        subtitulo = buildAnnotatedString {
                            append("Revisa la bandeja de ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(vm.correoParaLogin) }
                            append(" (y la carpeta de spam). El código vence a los 15 minutos.")
                        },
                        subtituloExito = "Ya puedes entrar con tu nueva contraseña.",
                        onCambio = vm::cambiarCodigo,
                        onVerificar = vm::cambiarContrasena,
                        onReenviar = vm::reenviarCodigo,
                        textoBoton = "Cambiar contraseña",
                        textoExito = "Contraseña actualizada",
                        textoCambiar = "Usar otro correo",
                        onCambiar = vm::usarOtroCorreo,
                        campos = { CamposPassword(vm) }
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.Space16))
        TextButton(onClick = { volverAlLogin(conAviso = false) }, enabled = !vm.codigo.ocupado) {
            Text("Volver al inicio de sesión")
        }
    }
}

@Composable
private fun PasoCorreoRecuperar(vm: RecuperarViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        Spacer(Modifier.height(Dimens.Space24))
        TarjetaVidrio {
            vm.aviso?.let {
                AvisoError(it)
                Spacer(Modifier.height(Dimens.Space12))
            }
            BotiTextField(
                value = vm.correo,
                reservarAyuda = false,
                onValueChange = vm::cambiarCorreo,
                label = "Correo electrónico",
                leadingIcon = Icons.Rounded.Mail,
                isError = vm.errorCorreo != null,
                errorMessage = vm.errorCorreo,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.pedirCodigo() })
            )
            Spacer(Modifier.height(Dimens.Space12))
            BotiButton(
                text = when {
                    vm.enviando -> "Enviando…"
                    vm.codigo.espera.activa -> "Espera ${formatoReloj(vm.codigo.espera.segundos)}"
                    else -> "Enviar código"
                },
                onClick = vm::pedirCodigo,
                enabled = vm.correo.isNotBlank() && !vm.enviando && !vm.codigo.espera.activa,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CamposPassword(vm: RecuperarViewModel) {
    var verContrasena by remember { mutableStateOf(false) }
    val transformacion = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation()
    val ocupado = vm.codigo.ocupado
    BotiTextField(
        value = vm.password,
        reservarAyuda = false,
        onValueChange = vm::cambiarPassword,
        label = "Nueva contraseña",
        leadingIcon = Icons.Rounded.Lock,
        visualTransformation = transformacion,
        trailingIcon = {
            TextButton(onClick = { verContrasena = !verContrasena }) {
                Text(if (verContrasena) "Ocultar" else "Ver")
            }
        },
        isError = vm.errorPassword != null,
        errorMessage = vm.errorPassword,
        supportingText = "Mínimo 8 caracteres.",
        enabled = !ocupado,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
    )
    Spacer(Modifier.height(Dimens.Space12))
    BotiTextField(
        value = vm.confirmar,
        reservarAyuda = false,
        onValueChange = vm::cambiarConfirmar,
        label = "Repite la nueva contraseña",
        leadingIcon = Icons.Rounded.Lock,
        visualTransformation = transformacion,
        isError = vm.errorConfirmar != null,
        errorMessage = vm.errorConfirmar,
        enabled = !ocupado,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.cambiarContrasena() })
    )
}
