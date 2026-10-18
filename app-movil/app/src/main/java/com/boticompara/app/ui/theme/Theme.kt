package com.boticompara.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Esquema de color Material 3 de BotiCompara
private val LightColorScheme = lightColorScheme(
    primary = VerdePrimario,
    onPrimary = SobreVerdePrimario,
    primaryContainer = VerdeContenedor,
    onPrimaryContainer = SobreVerdeContenedor,
    secondary = Secundario,
    onSecondary = SobreSecundario,
    secondaryContainer = SecundarioContenedor,
    onSecondaryContainer = SobreSecundarioContenedor,
    tertiary = Terciario,
    onTertiary = SobreTerciario,
    tertiaryContainer = TerciarioContenedor,
    onTertiaryContainer = SobreTerciarioContenedor,
    error = ErrorClaro,
    onError = SobreErrorClaro,
    errorContainer = ErrorContenedorClaro,
    onErrorContainer = SobreErrorContenedorClaro,
    background = FondoClaro,
    onBackground = SobreFondoClaro,
    surface = FondoClaro,
    onSurface = SobreFondoClaro,
    surfaceVariant = SuperficieVarianteClaro,
    onSurfaceVariant = SobreSuperficieVarianteClaro,
    outline = ContornoClaro,
    outlineVariant = ContornoVarianteClaro
)

private val DarkColorScheme = darkColorScheme(
    primary = VerdePrimarioOscuro,
    onPrimary = SobreVerdePrimarioOscuro,
    primaryContainer = VerdeContenedorOscuro,
    onPrimaryContainer = SobreVerdeContenedorOscuro,
    secondary = SecundarioOscuro,
    onSecondary = SobreSecundarioOscuro,
    secondaryContainer = SecundarioContenedorOscuro,
    onSecondaryContainer = SobreSecundarioContenedorOscuro,
    tertiary = TerciarioOscuro,
    onTertiary = SobreTerciarioOscuro,
    tertiaryContainer = TerciarioContenedorOscuro,
    onTertiaryContainer = SobreTerciarioContenedorOscuro,
    error = ErrorOscuro,
    onError = SobreErrorOscuro,
    errorContainer = ErrorContenedorOscuro,
    onErrorContainer = SobreErrorContenedorOscuro,
    background = FondoOscuro,
    onBackground = SobreFondoOscuro,
    surface = FondoOscuro,
    onSurface = SobreFondoOscuro,
    surfaceVariant = SuperficieVarianteOscuro,
    onSurfaceVariant = SobreSuperficieVarianteOscuro,
    outline = ContornoOscuro,
    outlineVariant = ContornoVarianteOscuro
)

@Composable
fun BotiComparaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Sin colores dinámicos: la app usa siempre la paleta de BotiCompara
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}