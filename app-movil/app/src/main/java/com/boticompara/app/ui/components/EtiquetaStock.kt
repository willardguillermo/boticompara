package com.boticompara.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boticompara.app.ui.theme.BotiComparaAppTheme

@Composable
fun EtiquetaStock(stock: Int, modifier: Modifier = Modifier) {
    val pocoStock = stock <= 10
    Etiqueta(
        texto = if (pocoStock) "Quedan $stock" else "Stock: $stock",
        modifier = modifier,
        icono = if (pocoStock) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
        fondo = if (pocoStock) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.primaryContainer,
        color = if (pocoStock) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onPrimaryContainer
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun EtiquetaStockPreview() {
    BotiComparaAppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtiquetaStock(stock = 5)
                EtiquetaStock(stock = 50)
            }
        }
    }
}
