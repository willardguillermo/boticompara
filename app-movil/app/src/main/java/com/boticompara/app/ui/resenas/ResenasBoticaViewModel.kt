package com.boticompara.app.ui.resenas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.remote.dto.ResenasResponse
import com.boticompara.app.data.repository.ResenaRepository
import com.boticompara.app.data.repository.ResultadoResena
import com.boticompara.app.data.repository.ResultadoResenas
import kotlinx.coroutines.launch

class ResenasBoticaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ResenaRepository(application)
    private var boticaId: Long = 0L

    var datos by mutableStateOf<ResenasResponse?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set
    var cargandoMas by mutableStateOf(false)
        private set
    var mensajeErrorPaginacion by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set
    var guardando by mutableStateOf(false)
        private set
    var mensajeFormulario by mutableStateOf<String?>(null)
        private set
    var guardado by mutableStateOf(false)
        private set

    fun cargar(id: Long) {
        boticaId = id
        viewModelScope.launch {
            isLoading = datos?.boticaId != id
            mensajeError = null
            when (val resultado = repository.listar(id, 0)) {
                is ResultadoResenas.Exito -> {
                    datos = resultado.resenas
                    mensajeErrorPaginacion = null
                }
                ResultadoResenas.SesionVencida -> sesionVencida = true
                is ResultadoResenas.Rechazada -> mensajeError = resultado.mensaje
                ResultadoResenas.SinConexion -> mensajeError = "No se pudo conectar con el servidor"
                ResultadoResenas.ErrorServidor -> mensajeError = "No se pudieron cargar las reseñas"
            }
            isLoading = false
        }
    }

    fun cargarSiguiente() {
        val actuales = datos ?: return
        if (cargandoMas || !ReglasResena.hayMasPaginas(actuales)) return
        val pagina = actuales.page + 1
        viewModelScope.launch {
            cargandoMas = true
            mensajeErrorPaginacion = null
            when (val resultado = repository.listar(boticaId, pagina)) {
                is ResultadoResenas.Exito -> {
                    datos = resultado.resenas.copy(
                        resenas = ReglasResena.agregarPagina(actuales.resenas, resultado.resenas.resenas)
                    )
                }
                ResultadoResenas.SesionVencida -> sesionVencida = true
                is ResultadoResenas.Rechazada -> mensajeErrorPaginacion = resultado.mensaje
                ResultadoResenas.SinConexion ->
                    mensajeErrorPaginacion = "No se pudo conectar con el servidor"
                ResultadoResenas.ErrorServidor ->
                    mensajeErrorPaginacion = "No se pudieron cargar más reseñas"
            }
            cargandoMas = false
        }
    }

    fun guardar(estrellas: Int, comentario: String) {
        ReglasResena.mensajeValidacion(estrellas, comentario)?.let {
            mensajeFormulario = it
            return
        }
        if (guardando) return
        viewModelScope.launch {
            guardando = true
            mensajeFormulario = null
            when (
                val resultado = repository.guardar(
                    boticaId,
                    estrellas,
                    ReglasResena.normalizarComentario(comentario)
                )
            ) {
                is ResultadoResena.Exito -> {
                    guardado = true
                    cargar(boticaId)
                }
                ResultadoResena.SesionVencida -> sesionVencida = true
                is ResultadoResena.Rechazada -> mensajeFormulario = resultado.mensaje
                ResultadoResena.SinConexion ->
                    mensajeFormulario = "No se pudo conectar con el servidor"
                ResultadoResena.ErrorServidor ->
                    mensajeFormulario = "No se pudo guardar la reseña"
            }
            guardando = false
        }
    }

    fun mensajeFormularioMostrado() {
        mensajeFormulario = null
    }

    fun guardadoMostrado() {
        guardado = false
    }
}
