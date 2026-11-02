package com.boticompara.app.vistas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.local.UbicacionManual
import com.boticompara.app.data.local.UbicacionManualStore
import com.boticompara.app.data.local.UbicacionProvider
import com.boticompara.app.data.remote.ProductoResponse
import com.boticompara.app.data.repository.ProductoRepository
import com.boticompara.app.data.repository.ResultadoBusqueda
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ViewModel (MVVM) de la búsqueda: valida, llama al Repository y expone el estado a la vista
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductoRepository(application)
    private val ubicacionProvider = UbicacionProvider(application)
    private val ubicacionStore = UbicacionManualStore(application)

    companion object {
        // H58: radios disponibles en kilómetros; null significa "Sin límite"
        val OPCIONES_RADIO: List<Double?> = listOf(2.0, 5.0, 10.0, null)
    }

    // H26: tiempo de espera desde la última tecla antes de buscar
    private val esperaDebounceMs = 500L
    private var busquedaJob: Job? = null

    var productos by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)   // H40: deslizar para actualizar
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set
    var searchType by mutableStateOf("nombre")   // "nombre" (H22) o "principio" (H23)
        private set

    // H27: ordenar por cercanía
    var ordenCercanas by mutableStateOf(false)
        private set
    var buscandoUbicacion by mutableStateOf(false)
        private set
    var mensajeUbicacion by mutableStateOf<String?>(null)
        private set
    private var ubicacion: UbicacionProvider.Coordenadas? = null

    // H58: radio de búsqueda en km (null = sin límite). Solo se envía si hay ubicación
    var radioKm by mutableStateOf<Double?>(null)
        private set

    // H48: alternar entre la lista de resultados y el mapa de boticas
    var vistaMapa by mutableStateOf(false)
        private set

    fun cambiarVista(mapa: Boolean) {
        vistaMapa = mapa
    }

    // H50: ubicación elegida a mano (distrito o punto en el mapa); tiene prioridad sobre el GPS
    var ubicacionManual by mutableStateOf<UbicacionManual?>(null)
        private set
    var mostrarSelector by mutableStateOf(false)
        private set

    init {
        // H50: recupera la ubicación manual guardada de una sesión anterior
        viewModelScope.launch {
            val guardada = ubicacionStore.obtener() ?: return@launch
            ubicacionManual = guardada
            ubicacion = UbicacionProvider.Coordenadas(guardada.lat, guardada.lng)
            ordenCercanas = true
        }
    }

    // H26: cada tecla reinicia la espera; solo se busca cuando el usuario deja de escribir
    fun onQueryChange(texto: String) {
        searchQuery = texto
        programarBusqueda(inmediata = false)
    }

    // Al cambiar el criterio se vuelve a buscar sin esperar
    fun onTipoChange(tipo: String) {
        if (tipo == searchType) return
        searchType = tipo
        programarBusqueda(inmediata = true)
    }

    // H58: al cambiar el radio se vuelve a buscar sin esperar
    fun onRadioChange(valor: Double?) {
        if (valor == radioKm) return
        radioKm = valor
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

    // Tecla "Buscar" del teclado: busca al instante
    fun buscarProductos() {
        if (searchQuery.trim().length < 2) {
            busquedaJob?.cancel()
            productos = emptyList()
            isLoading = false
            mensaje = "Escribe al menos 2 letras"
            return
        }
        programarBusqueda(inmediata = true)
    }

    // H40: deslizar hacia abajo para actualizar los precios
    fun refrescar() {
        val query = searchQuery.trim()
        if (query.length < 2) return
        busquedaJob?.cancel()
        busquedaJob = viewModelScope.launch {
            isRefreshing = true
            ejecutarBusqueda(query)
            isRefreshing = false
        }
    }

    // H40: al volver a la pantalla se recarga si había una búsqueda
    fun recargarAlVolver() {
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

    // Botón de borrar del campo de búsqueda
    fun limpiar() {
        busquedaJob?.cancel()
        searchQuery = ""
        productos = emptyList()
        mensaje = null
        isLoading = false
        isRefreshing = false
    }

    // ---------- H27: ubicación y "Más cercanas" ----------
    fun tienePermisoUbicacion(): Boolean = ubicacionProvider.tienePermiso()

    // Obtiene la ubicación (la manual si existe, si no el GPS) y reordena por distancia
    fun activarCercanas() {
        viewModelScope.launch {
            buscandoUbicacion = true
            mensajeUbicacion = null
            val manual = ubicacionManual
            val coordenadas = if (manual != null) {
                UbicacionProvider.Coordenadas(manual.lat, manual.lng)
            } else {
                ubicacionProvider.obtener()
            }
            buscandoUbicacion = false
            if (coordenadas == null) {
                ordenCercanas = false
                mensajeUbicacion = "No pudimos obtener tu ubicación. Activa el GPS o elige tu ubicación manualmente"
            } else {
                ubicacion = coordenadas
                ordenCercanas = true
                if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
            }
        }
    }

    fun desactivarCercanas() {
        ordenCercanas = false
        mensajeUbicacion = null
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

    // H50: sin permiso de GPS se ofrece elegir la ubicación a mano
    fun permisoDenegado() {
        ordenCercanas = false
        mensajeUbicacion = "Sin permiso de ubicación no podemos usar tu GPS. Elige tu ubicación manualmente"
        mostrarSelector = true
    }

    // ---------- H50: ubicación manual ----------
    fun abrirSelector() {
        mostrarSelector = true
    }

    fun cerrarSelector() {
        mostrarSelector = false
    }

    // Guarda la ubicación elegida y la usa de inmediato en la búsqueda
    fun fijarUbicacionManual(etiqueta: String, lat: Double, lng: Double) {
        val nueva = UbicacionManual(etiqueta, lat, lng)
        ubicacionManual = nueva
        ubicacion = UbicacionProvider.Coordenadas(lat, lng)
        ordenCercanas = true
        mensajeUbicacion = null
        mostrarSelector = false
        viewModelScope.launch { ubicacionStore.guardar(nueva) }
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

    // Vuelve al GPS: borra la ubicación manual guardada
    fun quitarUbicacionManual() {
        ubicacionManual = null
        ubicacion = null
        ordenCercanas = false
        mostrarSelector = false
        viewModelScope.launch { ubicacionStore.borrar() }
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

    private fun programarBusqueda(inmediata: Boolean) {
        busquedaJob?.cancel()   // cancela la búsqueda anterior si todavía no terminó
        isRefreshing = false
        val query = searchQuery.trim()
        if (query.length < 2) {
            productos = emptyList()
            mensaje = null
            isLoading = false
            return
        }
        busquedaJob = viewModelScope.launch {
            if (!inmediata) delay(esperaDebounceMs)
            ejecutarBusqueda(query)
        }
    }

    private suspend fun ejecutarBusqueda(query: String) {
        isLoading = true
        mensaje = null
        val coordenadas = ubicacion
        val conUbicacion = ordenCercanas && coordenadas != null
        val resultado = if (conUbicacion && coordenadas != null) {
            // H27, H50 y H58: con ubicación se ordena por distancia y se aplica el radio elegido
            repository.buscar(query, searchType, "distancia", coordenadas.lat, coordenadas.lng, radioKm)
        } else {
            repository.buscar(query, searchType, "precio_asc")   // H24
        }
        when (resultado) {
            is ResultadoBusqueda.Exito -> {
                productos = resultado.productos
                if (productos.isEmpty()) {
                    val radio = radioKm
                    mensaje = if (conUbicacion && radio != null) {
                        "No encontramos ese medicamento a ${radio.toInt()} km o menos de ti. Prueba con un radio mayor"
                    } else {
                        "No encontramos ese medicamento"
                    }
                }
            }
            ResultadoBusqueda.SesionVencida -> sesionVencida = true
            ResultadoBusqueda.SinConexion -> {
                productos = emptyList()
                mensaje = "No se pudo conectar con el servidor"
            }
            ResultadoBusqueda.ErrorServidor -> {
                productos = emptyList()
                mensaje = "No se pudo realizar la búsqueda"
            }
        }
        isLoading = false
    }
}