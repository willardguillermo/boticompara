package com.boticompara.app.vistas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun SearchScreen(navController: NavController, searchViewModel: SearchViewModel = viewModel()) {

    // Si el token venció, volver al login
    LaunchedEffect(searchViewModel.sesionVencida) {
        if (searchViewModel.sesionVencida) {
            navController.navigate("login") { popUpTo(0) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Buscar medicamentos", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { navController.navigate("perfil") }) { Text("Mi perfil") }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchViewModel.searchQuery,
            onValueChange = { searchViewModel.searchQuery = it },
            label = {
                Text(if (searchViewModel.searchType == "nombre") "Nombre comercial (ej. Panadol)"
                else "Principio activo (ej. Paracetamol)")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Selector de criterio (H22 / H23): el elegido queda marcado
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(
                selected = searchViewModel.searchType == "nombre",
                onClick = { searchViewModel.searchType = "nombre" },
                label = { Text("Por nombre") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = searchViewModel.searchType == "principio",
                onClick = { searchViewModel.searchType = "principio" },
                label = { Text("Por principio activo") }
            )
        }

        Button(
            onClick = { searchViewModel.buscarProductos() },
            enabled = !searchViewModel.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Buscar") }

        Spacer(Modifier.height(16.dp))

        when {
            searchViewModel.isLoading ->
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            searchViewModel.mensaje != null ->
                Text(searchViewModel.mensaje!!, style = MaterialTheme.typography.bodyLarge)

            searchViewModel.productos.isNotEmpty() -> {
                Text(
                    "${searchViewModel.productos.size} resultados, del más barato al más caro",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchViewModel.productos) { producto ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(producto.nombreComercial, style = MaterialTheme.typography.titleMedium)
                                Text("Principio activo: ${producto.principioActivo}")
                                Text("Presentación: ${producto.presentacion}")
                                Text(
                                    "S/ ${"%.2f".format(producto.precio)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Stock: ${producto.stock}")
                                Text("${producto.boticaNombre} · ${producto.boticaDireccion}, ${producto.boticaDistrito}")
                            }
                        }
                    }
                }
            }
        }
    }
}