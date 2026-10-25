package com.boticompara.app.vistas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ===================== COMPONENTES COMPARTIDOS (Material 3) =====================

// Ícono dentro de un círculo de color
@Composable
fun IconoCircular(
    icono: ImageVector,
    modifier: Modifier = Modifier,
    tamano: Dp = 40.dp,
    fondo: Color = MaterialTheme.colorScheme.secondaryContainer,
    color: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Box(
        modifier = modifier
            .size(tamano)
            .background(fondo, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(tamano * 0.5f)
        )
    }
}

// Etiqueta en forma de pastilla, con ícono opcional
@Composable
fun Etiqueta(
    texto: String,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    fondo: Color = MaterialTheme.colorScheme.secondaryContainer,
    color: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(modifier = modifier, color = fondo, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icono != null) {
                Icon(
                    imageVector = icono,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                texto,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Stock: naranja si quedan 10 o menos, verde si hay más
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

// Texto con un ícono pequeño al inicio (dirección, distancia, etc.)
@Composable
fun LineaConIcono(
    icono: ImageVector,
    texto: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    estilo: TextStyle = MaterialTheme.typography.bodySmall,
    peso: FontWeight? = null,
    maxLineas: Int = 2
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            texto,
            style = estilo,
            color = color,
            fontWeight = peso,
            maxLines = maxLineas,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Para "sin resultados", errores o el inicio de la búsqueda
@Composable
fun EstadoVacio(icono: ImageVector, texto: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        IconoCircular(icono = icono, tamano = 80.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}