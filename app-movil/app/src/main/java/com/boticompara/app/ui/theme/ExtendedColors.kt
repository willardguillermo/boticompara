package com.boticompara.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val ahorro: Color,
    val onAhorro: Color,
    val ahorroContainer: Color,
    val onAhorroContainer: Color,
    val alerta: Color,
    val onAlerta: Color,
    val alertaContainer: Color,
    val onAlertaContainer: Color,
    val stockBajo: Color,
    val onStockBajo: Color,
    val stockBajoContainer: Color,
    val onStockBajoContainer: Color,
    val exito: Color,
    val onExito: Color,
    val exitoContainer: Color,
    val onExitoContainer: Color
)

val LightExtendedColors = ExtendedColors(
    ahorro = Color(0xFF006C45),
    onAhorro = Color(0xFFFFFFFF),
    ahorroContainer = Color(0xFF8AF8BE),
    onAhorroContainer = Color(0xFF002112),
    alerta = Color(0xFF7A5900),
    onAlerta = Color(0xFFFFFFFF),
    alertaContainer = Color(0xFFFFDEA3),
    onAlertaContainer = Color(0xFF261A00),
    stockBajo = Color(0xFF984100),
    onStockBajo = Color(0xFFFFFFFF),
    stockBajoContainer = Color(0xFFFFDBC9),
    onStockBajoContainer = Color(0xFF331300),
    exito = Color(0xFF006D3B),
    onExito = Color(0xFFFFFFFF),
    exitoContainer = Color(0xFF8CF6B3),
    onExitoContainer = Color(0xFF00210E)
)

val DarkExtendedColors = ExtendedColors(
    ahorro = Color(0xFF6EDBA4),
    onAhorro = Color(0xFF003821),
    ahorroContainer = Color(0xFF005232),
    onAhorroContainer = Color(0xFF8AF8BE),
    alerta = Color(0xFFEEC059),
    onAlerta = Color(0xFF402D00),
    alertaContainer = Color(0xFF5D4200),
    onAlertaContainer = Color(0xFFFFDEA3),
    stockBajo = Color(0xFFFFB68E),
    onStockBajo = Color(0xFF532100),
    stockBajoContainer = Color(0xFF753200),
    onStockBajoContainer = Color(0xFFFFDBC9),
    exito = Color(0xFF6FD998),
    onExito = Color(0xFF00391C),
    exitoContainer = Color(0xFF00522B),
    onExitoContainer = Color(0xFF8CF6B3)
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
