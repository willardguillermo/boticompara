package com.boticompara.app.auth

import com.boticompara.app.ui.auth.PasoRecuperar
import com.boticompara.app.ui.auth.RecuperarViewModel
import com.boticompara.app.ui.auth.codigo.FaseCodigo
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecuperarViewModelTest : PruebaConMain() {

    private val repo = AccesoFalso()
    private val correo = "carlos@boticompara.nissidev.dev"

    private fun TestScope.enPasoCodigo(): RecuperarViewModel {
        val vm = RecuperarViewModel(repo)
        vm.cambiarCorreo(correo)
        vm.pedirCodigo()
        runCurrent()
        return vm
    }

    private fun RecuperarViewModel.llenar(codigo: String = "483920", password: String = "NuevaClave2026!") {
        cambiarCodigo(codigo)
        cambiarPassword(password)
        cambiarConfirmar(password)
    }

    @Test
    fun pedirCodigoMuestraElMensajeDelServidorYPasaAlCodigo() = runTest {
        val vm = enPasoCodigo()
        assertEquals(PasoRecuperar.CODIGO, vm.paso)
        assertEquals(
            "Si el correo está registrado, te enviamos las instrucciones para restablecer tu contraseña.",
            vm.mensajeServidor
        )
        assertEquals(60, vm.codigo.espera.segundos)
    }

    @Test
    fun limite429SeQuedaEnElCorreoConLaEspera() = runTest {
        repo.pedirRecuperacion = fallo(429, "Hiciste demasiadas solicitudes desde esta conexión. Intenta más tarde", retryAfter = "300")
        val vm = enPasoCodigo()
        assertEquals(PasoRecuperar.CORREO, vm.paso)
        assertEquals("Hiciste demasiadas solicitudes desde esta conexión. Intenta más tarde.", vm.aviso)
        assertEquals(300, vm.codigo.espera.segundos)
    }

    @Test
    fun completarElCodigoNoLoEnviaSolo() = runTest {
        val vm = enPasoCodigo()
        vm.cambiarCodigo("483920")
        avanzar(5_000)
        assertTrue(repo.llamadas.none { it.startsWith("restablecer") })
        assertEquals(FaseCodigo.ESCRIBIENDO, vm.codigo.fase)
    }

    @Test
    fun validaLaContrasenaAntesDeEnviar() = runTest {
        val vm = enPasoCodigo()
        vm.llenar(password = "corta")
        vm.cambiarContrasena()
        runCurrent()
        assertEquals("La contraseña debe tener al menos 8 caracteres.", vm.errorPassword)
        assertTrue(repo.llamadas.none { it.startsWith("restablecer") })
    }

    @Test
    fun faltaElCodigoYLaContrasenaNoCoincide() = runTest {
        val vm = enPasoCodigo()
        vm.cambiarCodigo("12")
        vm.cambiarPassword("NuevaClave2026!")
        vm.cambiarConfirmar("Otra2026!")
        vm.cambiarContrasena()
        assertEquals("Las contraseñas no coinciden.", vm.errorConfirmar)
        assertEquals("Escribe los 6 dígitos del código.", vm.codigo.errorCodigo)
    }

    @Test
    fun errorDelCodigoFallaLaCruzYLimpiaLasCasillas() = runTest {
        repo.restablecer = fallo(400, "El código no es válido o venció. Pide uno nuevo")
        val vm = enPasoCodigo()
        vm.llenar()
        vm.cambiarContrasena()
        avanzar(900)
        assertEquals(FaseCodigo.FALLA, vm.codigo.fase)
        avanzar(1_250)
        assertEquals("El código no es válido o venció. Pide uno nuevo.", vm.codigo.errorCodigo)
        assertEquals("", vm.codigo.codigo)
        assertNull(vm.errorPassword)
        assertEquals("NuevaClave2026!", vm.password)   // la contraseña escrita se queda
    }

    @Test
    fun errorDeLaContrasenaVaEnElCampoYNoBorraElCodigo() = runTest {
        repo.restablecer = fallo(400, "La contraseña debe tener entre 8 y 72 caracteres")
        val vm = enPasoCodigo()
        vm.llenar()
        vm.cambiarContrasena()
        avanzar(900)
        assertEquals(FaseCodigo.VOLVER, vm.codigo.fase)   // sin parpadeo rojo
        avanzar(600)
        assertEquals("La contraseña debe tener entre 8 y 72 caracteres.", vm.errorPassword)
        assertEquals("483920", vm.codigo.codigo)
        assertNull(vm.codigo.errorCodigo)
        assertEquals(0, vm.codigo.sacudidas)
    }

    @Test
    fun contrasenaCambiadaVuelveAlLoginSinGuardarLaContrasena() = runTest {
        val vm = enPasoCodigo()
        vm.llenar()
        vm.cambiarContrasena()
        avanzar(2_300)
        assertTrue(vm.restablecido)
        assertEquals("483920", repo.ultimoCodigo)
        assertEquals("NuevaClave2026!", repo.ultimaPassword)
        assertEquals("", vm.password)
        assertEquals(correo, vm.correoParaLogin)
    }

    @Test
    fun usarOtroCorreoVuelveAlPrimerPaso() = runTest {
        val vm = enPasoCodigo()
        vm.llenar()
        vm.usarOtroCorreo()
        assertEquals(PasoRecuperar.CORREO, vm.paso)
        assertEquals("", vm.password)
        assertEquals("", vm.codigo.codigo)
        assertFalse(vm.restablecido)
    }
}
