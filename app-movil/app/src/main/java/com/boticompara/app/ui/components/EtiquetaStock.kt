package com.boticompara.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun EtiquetaStock(stock: Int, modifier: Modifier = Modifier) {
    val pocoStock = stock <= 10
    Etiqueta(
        texto = if (pocoStock) "Quedan $stock" else "Stock: $stock",
        modifier = modifier,
        icono = if (pocoStock) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
        fondo = if (pocoStock) androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
        else androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
        color = if (pocoStock) androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer
        else androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
    )
}
