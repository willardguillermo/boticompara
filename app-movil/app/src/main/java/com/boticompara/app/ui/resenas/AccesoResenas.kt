package com.boticompara.app.ui.resenas

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.repository.ResenaRepository
import com.boticompara.app.data.repository.ResultadoResenas
import com.boticompara.app.ui.components.BotiOutlinedButton
import kotlinx.coroutines.launch

class ResumenResenasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ResenaRepository(application)

    var promedio by mutableStateOf<Double?>(null)
        private set
    var total by mutableStateOf(0)
        private set
    var cargando by mutableStateOf(true)
        private set
    var error by mutableStateOf(false)
        private set

    fun cargar(boticaId: Long) {
        viewModelScope.launch {
            cargando = true
            error = false
            when (val resultado = repository.listar(boticaId, 0)) {
                is ResultadoResenas.Exito -> {
                    promedio = resultado.resenas.promedio
                    total = resultado.resenas.total
                }
                else -> {
                    promedio = null
                    total = 0
                    error = true
                }
            }
            cargando = false
        }
    }
}

@Composable
fun AccesoResenas(
    boticaId: Long,
    navController: NavController,
    viewModel: ResumenResenasViewModel = viewModel()
) {
    LaunchedEffect(boticaId) { viewModel.cargar(boticaId) }

    val texto = when {
        viewModel.cargando || viewModel.error -> "Ver reseñas"
        else -> ReglasResena.textoResumen(viewModel.promedio, viewModel.total)
    }

    BotiOutlinedButton(
        text = texto,
        onClick = { navController.navigate("resenas/$boticaId") },
        icon = Icons.Rounded.Star,
        modifier = Modifier.fillMaxWidth()
    )
}
