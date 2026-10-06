package com.boticompara.app.vistas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Medicine(val name: String, val pharmacy: String, val price: String)

@Composable
fun SearchScreen() {
    var searchQuery by remember { mutableStateOf("") }

    val allMedicines = listOf(
        Medicine("Paracetamol 500mg", "InkaFarma", "S/ 12.50"),
        Medicine("Paracetamol 500mg", "Mifarma", "S/ 10.00"),
        Medicine("Ibuprofeno 400mg", "Boticas Perú", "S/ 15.00"),
        Medicine("Amoxicilina 500mg", "InkaFarma", "S/ 25.40"),
        Medicine("Aspirina 100mg", "Mifarma", "S/ 8.50")
    )

    val filteredMedicines = allMedicines.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(text = "Buscar Medicamentos", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Escribe el nombre del medicamento...") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (filteredMedicines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No se encontraron medicamentos", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMedicines) { med ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = med.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Farmacia: ${med.pharmacy}", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "Precio: ${med.price}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}