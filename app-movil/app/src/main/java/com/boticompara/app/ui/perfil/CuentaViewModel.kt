package com.boticompara.app.ui.perfil

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boticompara.app.data.repository.CuentaRepository
import com.boticompara.app.data.repository.ResultadoCuenta
import kotlinx.coroutines.launch

// H19: eliminar la cuenta. La recuperación de la contraseña (H17) está en ui/auth/RecuperarViewModel.
class CuentaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CuentaRepository(application)

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var cuentaEliminada by mutableStateOf(false)
        private set
    var sesionVencida by mutableStateOf(false)
        private set

    fun eliminarCuenta() {
        viewModelScope.launch {
            isLoading = true
            error = null
            when (repository.eliminarCuenta()) {
                ResultadoCuenta.Exito -> cuentaEliminada = true
                ResultadoCuenta.SesionVencida -> sesionVencida = true
                ResultadoCuenta.SinConexion -> error = "No se pudo conectar con el servidor"
                else -> error = "No se pudo eliminar la cuenta"
            }
            isLoading = false
        }
    }

}
