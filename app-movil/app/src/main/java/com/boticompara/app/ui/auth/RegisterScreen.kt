package com.boticompara.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.boticompara.app.ui.auth.codigo.InsigniaVerificado
import com.boticompara.app.ui.auth.codigo.TarjetaCodigo
import com.boticompara.app.ui.auth.codigo.formatoReloj
import com.boticompara.app.ui.components.BotiButton
import com.boticompara.app.ui.components.BotiTextField
import com.boticompara.app.ui.components.TarjetaVidrio
import com.boticompara.app.ui.theme.Dimens

// H1, H15, H20 - Registro del comprador en 3 pasos: correo → código de 6 dígitos → datos de la cuenta.
// La cuenta se crea solo al final, con el tokenVerificacion (ver RegistroViewModel). Los dueños se registran en la web.
@Composable
fun RegisterScreen(
    navController: NavController,
    vm: RegistroViewModel = viewModel(factory = RegistroViewModel.Factory)
) {
    LaunchedEffect(vm.registrado) {
        if (vm.registrado) {
            navController.navigate("busqueda") { popUpTo("login") { inclusive = true } }
        }
    }
    // Atrás desde el código o la cuenta vuelve al correo (y olvida el token)
    BackHandler(enabled = vm.paso != PasoRegistro.CORREO && !vm.verificandoCodigo && !vm.enviando) {
        vm.volverAlCorreo()
    }

    fun irAlLogin() {
        // El login recibe el correo escrito para no volver a teclearlo
        navController.previousBackStackEntry?.savedStateHandle?.set(CLAVE_CORREO_LOGIN, vm.correo.trim())
        if (!navController.popBackStack("login", inclusive = false)) navController.navigate("login")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Dimens.Space16))
        Text(
            "Crea tu cuenta",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Regístrate gratis y empieza a comparar precios.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.Space16))
        IndicadorPasos(vm.paso)
        Spacer(Modifier.height(Dimens.Space20))

        AnimatedContent(
            targetState = vm.paso,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "paso"
        ) { paso ->
            when (paso) {
                PasoRegistro.CORREO -> PasoCorreo(vm, onIniciarSesion = ::irAlLogin)
                PasoRegistro.CODIGO -> TarjetaCodigo(
                    estado = vm.codigo,
                    titulo = "Verifica tu correo",
                    subtitulo = buildAnnotatedString {
                        append("Escribe el código que enviamos a ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(vm.correoVerificado) }
                        append(". Vence en 15 minutos; si no lo ves, revisa la carpeta de spam.")
                    },
                    subtituloExito = "Listo. Ahora completa los datos de tu cuenta.",
                    onCambio = vm::cambiarCodigo,
                    onVerificar = vm::verificarCodigo,
                    onReenviar = vm::reenviarCodigo,
                    textoCambiar = "Cambiar correo",
                    onCambiar = vm::volverAlCorreo
                )
                PasoRegistro.CUENTA -> PasoCuenta(vm)
            }
        }

        Spacer(Modifier.height(Dimens.Space16))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿Ya tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = ::irAlLogin) {
                Text("Inicia sesión", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(Dimens.Space24))
    }
}

/** Clave del savedStateHandle del login para recibir el correo (desde el registro y la recuperación). */
const val CLAVE_CORREO_LOGIN = "correo"

@Composable
private fun IndicadorPasos(paso: PasoRegistro) {
    val nombres = listOf("Correo", "Código", "Cuenta")
    val actual = paso.ordinal
    val esquema = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Paso ${actual + 1} de 3: ${nombres[actual]}"
            }
    ) {
        nombres.forEachIndexed { i, nombre ->
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            if (i <= actual) esquema.primary else esquema.outlineVariant,
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(Modifier.height(Dimens.Space4))
                Text(
                    "${i + 1}. $nombre",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == actual) esquema.primary else esquema.onSurfaceVariant,
                    fontWeight = if (i == actual) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PasoCorreo(vm: RegistroViewModel, onIniciarSesion: () -> Unit) {
    TarjetaVidrio {
        Text(
            "Tu correo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Dimens.Space4))
        Text(
            "Empieza con tu correo: te enviaremos un código de 6 dígitos para verificarlo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        vm.aviso?.let { AvisoError(it) }
        Spacer(Modifier.height(Dimens.Space16))

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
        // 409: el correo ya tiene cuenta
        if (vm.correoYaRegistrado) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿Es tu cuenta?", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onIniciarSesion) {
                    Text("Inicia sesión", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(Dimens.Space12))

        BotiButton(
            text = when {
                vm.enviando -> "Enviando código…"
                vm.esperaPedir.activa -> "Espera ${formatoReloj(vm.esperaPedir.segundos)}"
                else -> "Enviar código"
            },
            onClick = vm::pedirCodigo,
            enabled = vm.correo.isNotBlank() && !vm.enviando && !vm.esperaPedir.activa,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Dimens.Space12))
        AvisoPrivacidad()
    }
}

@Composable
private fun PasoCuenta(vm: RegistroViewModel) {
    var verContrasena by remember { mutableStateOf(false) }
    val transformacion = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation()

    TarjetaVidrio {
        InsigniaVerificado(correo = vm.correoVerificado)
        Spacer(Modifier.height(Dimens.Space16))
        Text("Tu cuenta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Con esta cuenta compararás precios, guardarás favoritos y harás reservas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        vm.aviso?.let { AvisoError(it) }
        Spacer(Modifier.height(Dimens.Space12))

        BotiTextField(
            value = vm.nombre,
            reservarAyuda = false,
            onValueChange = vm::cambiarNombre,
            label = "Nombre completo",
            leadingIcon = Icons.Rounded.Person,
            isError = vm.errorNombre != null,
            errorMessage = vm.errorNombre,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(Dimens.Space12))
        BotiTextField(
            value = vm.password,
            reservarAyuda = false,
            onValueChange = vm::cambiarPassword,
            label = "Contraseña",
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(Dimens.Space12))
        BotiTextField(
            value = vm.confirmar,
            reservarAyuda = false,
            onValueChange = vm::cambiarConfirmar,
            label = "Repite la contraseña",
            leadingIcon = Icons.Rounded.Lock,
            visualTransformation = transformacion,
            isError = vm.errorConfirmar != null,
            errorMessage = vm.errorConfirmar,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(Dimens.Space12))
        BotiTextField(
            value = vm.telefono,
            reservarAyuda = false,
            onValueChange = vm::cambiarTelefono,
            label = "Teléfono (opcional)",
            leadingIcon = Icons.Rounded.Phone,
            isError = vm.errorTelefono != null,
            errorMessage = vm.errorTelefono,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.crearCuenta() })
        )
        Spacer(Modifier.height(Dimens.Space12))
        AvisoPrivacidad()
        Spacer(Modifier.height(Dimens.Space16))

        BotiButton(
            text = if (vm.enviando) "Creando tu cuenta…" else "Crear cuenta",
            onClick = vm::crearCuenta,
            enabled = !vm.enviando && !vm.registrado,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// H21: el registro solo pide datos de contacto y de acceso
@Composable
private fun AvisoPrivacidad() {
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
}

@Composable
internal fun AvisoError(texto: String) {
    Spacer(Modifier.height(Dimens.Space12))
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(
            texto,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Dimens.Space12)
        )
    }
}
