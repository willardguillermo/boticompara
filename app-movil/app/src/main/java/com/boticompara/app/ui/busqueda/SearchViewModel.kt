package com.boticompara.app.ui.busqueda

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.local.UbicacionManual
import com.boticompara.app.data.local.UbicacionManualStore
import com.boticompara.app.data.local.UbicacionProvider
import com.boticompara.app.data.remote.dto.ProductoResponse
import com.boticompara.app.data.repository.ProductoRepository
import com.boticompara.app.data.repository.ResultadoBusqueda
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductoRepository(application)
    private val ubicacionProvider = UbicacionProvider(application)
    private val ubicacionStore = UbicacionManualStore(application)

    companion object {
        val OPCIONES_RADIO: List<Double?> = listOf(2.0, 5.0, 10.0, null)
    }

    private val esperaDebounceMs = 500L
    private var busquedaJob: Job? = null

    var productos by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set
    var searchType by mutableStateOf("nombre")
        private set

    var ordenCercanas by mutableStateOf(false)
        private set
    var buscandoUbicacion by mutableStateOf(false)
        private set
    var mensajeUbicacion by mutableStateOf<String?>(null)
        private set
    private var ubicacion by mutableStateOf<UbicacionProvider.Coordenadas?>(null)

    var radioKm by mutableStateOf<Double?>(null)
        private set

    var vistaMapa by mutableStateOf(false)
        private set

    fun cambiarVista(mapa: Boolean) {
        vistaMapa = mapa
        if (mapa) prepararMapa()
    }

    // H48: dónde abre el mapa. Se muestra ya con lo que se sabe (GPS previo, manual o Lima centro)
    // y, si hay permiso, se pide una ubicación GPS nueva y el mapa se recentra al llegar.
    var ubicacionMapa by mutableStateOf<UbicacionInicial?>(null)
        private set
    private var trabajoMapa: Job? = null

    fun prepararMapa() {
        val manual = ubicacionManual?.let { Punto(it.lat, it.lng) }
        // Sin ubicación manual, la que haya en `ubicacion` vino del GPS (Más cercanas)
        val gpsPrevio = if (ubicacionManual == null) ubicacion?.let { Punto(it.lat, it.lng) } else null
        ubicacionMapa = elegirUbicacionInicial(gpsPrevio, manual)
        trabajoMapa?.cancel()
        if (!ubicacionProvider.tienePermiso()) return
        trabajoMapa = viewModelScope.launch {
            val gps = ubicacionProvider.obtener() ?: return@launch
            ubicacionMapa = elegirUbicacionInicial(Punto(gps.lat, gps.lng), manual)
        }
    }

    var ubicacionManual by mutableStateOf<UbicacionManual?>(null)
        private set
    var mostrarSelector by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val guardada = ubicacionStore.obtener() ?: return@launch
            ubicacionManual = guardada
            ubicacion = UbicacionProvider.Coordenadas(guardada.lat, guardada.lng)
            ordenCercanas = true
        }
    }

    fun onQueryChange(texto: String) {
        searchQuery = texto
        programarBusqueda(inmediata = false)
    }

    fun onTipoChange(tipo: String) {
        if (tipo == searchType) return
        searchType = tipo
        programarBusqueda(inmediata = true)
    }

    fun onRadioChange(valor: Double?) {
        if (valor == radioKm) return
        radioKm = valor
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

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

    fun recargarAlVolver() {
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

    fun limpiar() {
        busquedaJob?.cancel()
        searchQuery = ""
        productos = emptyList()
        mensaje = null
        isLoading = false
        isRefreshing = false
    }

    fun tienePermisoUbicacion(): Boolean = ubicacionProvider.tienePermiso()

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

    fun permisoDenegado() {
        ordenCercanas = false
        mensajeUbicacion = "Sin permiso de ubicación no podemos usar tu GPS. Elige tu ubicación manualmente"
        mostrarSelector = true
    }

    fun abrirSelector() {
        mostrarSelector = true
    }

    fun cerrarSelector() {
        mostrarSelector = false
    }

    fun fijarUbicacionManual(etiqueta: String, lat: Double, lng: Double) {
        val nueva = UbicacionManual(etiqueta, lat, lng)
        ubicacionManual = nueva
        ubicacion = UbicacionProvider.Coordenadas(lat, lng)
        ordenCercanas = true
        mensajeUbicacion = null
        mostrarSelector = false
        viewModelScope.launch { ubicacionStore.guardar(nueva) }
        if (vistaMapa) prepararMapa()
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

    fun quitarUbicacionManual() {
        ubicacionManual = null
        ubicacion = null
        ordenCercanas = false
        mostrarSelector = false
        viewModelScope.launch { ubicacionStore.borrar() }
        if (vistaMapa) prepararMapa()
        if (searchQuery.trim().length >= 2) programarBusqueda(inmediata = true)
    }

    private fun programarBusqueda(inmediata: Boolean) {
        busquedaJob?.cancel()
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
            repository.buscar(query, searchType, "distancia", coordenadas.lat, coordenadas.lng, radioKm)
        } else {
            repository.buscar(query, searchType, "precio_asc")
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
