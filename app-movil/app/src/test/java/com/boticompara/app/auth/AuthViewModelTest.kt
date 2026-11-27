package com.boticompara.app.auth

import com.boticompara.app.data.repository.ResultadoApi
import com.boticompara.app.data.repository.ResultadoGoogle
import com.boticompara.app.ui.auth.AuthViewModel
import com.boticompara.app.ui.auth.CORREO_NO_VERIFICADO
import com.boticompara.app.ui.auth.codigo.FaseCodigo
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthViewModelTest : PruebaConMain() {

    private val repo = AccesoFalso()
    private val vm = AuthViewModel(repo) { ResultadoGoogle.Cancelado }
    private val lucia = "lucia@boticompara.nissidev.dev"
    private val noVerificado = fallo(403, "Verifica tu correo para entrar", CORREO_NO_VERIFICADO)
    private var entro = 0

    @Test
    fun loginCorrectoGuardaLaSesionYEntra() = runTest {
        vm.login(" carlos@boticompara.nissidev.dev ", "Boti2026!") { entro++ }
        runCurrent()
        assertEquals(1, entro)
        assertEquals(SESION, repo.sesionGuardada)
        assertEquals(listOf("login:carlos@boticompara.nissidev.dev"), repo.llamadas)
    }

    @Test
    fun credencialesIncorrectas401() = runTest {
        repo.login = fallo(401, "Correo o contraseña incorrectos")
        vm.login("carlos@boticompara.nissidev.dev", "mala") { entro++ }
        runCurrent()
        assertEquals("Correo o contraseña incorrectos.", vm.errorMessage)
        assertEquals(0, entro)
        assertFalse(vm.isLoading)
    }

    @Test
    fun cuentaSuspendida403SinCodigoNoPideVerificar() = runTest {
        repo.login = fallo(403, "Tu cuenta está suspendida hasta el 30/11/2026")
        vm.login(lucia, "Boti2026!") { entro++ }
        runCurrent()
        assertEquals("Tu cuenta está suspendida hasta el 30/11/2026.", vm.errorMessage)
        assertNull(vm.verificacion)
    }

    @Test
    fun demasiadosIntentos429() = runTest {
        repo.login = fallo(429, "Demasiados intentos fallidos. Espera 15 minutos", retryAfter = "900")
        vm.login(lucia, "mala") { entro++ }
        runCurrent()
        assertEquals("Demasiados intentos fallidos. Espera 15 minutos.", vm.errorMessage)
    }

    @Test
    fun correoNoVerificadoPideElCodigoYAbreLaTarjeta() = runTest {
        repo.login = noVerificado
        vm.login(lucia, "Boti2026!") { entro++ }
        runCurrent()
        val estado = vm.verificacion
        assertNotNull(estado)
        assertEquals("Verifica tu correo para entrar.", vm.tituloVerificacion)
        assertEquals(lucia, vm.correoVerificacion)
        assertEquals(listOf("login:$lucia", "verificacion/codigo:$lucia"), repo.llamadas)
        assertEquals(60, estado!!.espera.segundos)
        assertNull(vm.errorMessage)
        assertEquals(0, entro)
    }

    @Test
    fun siNoSePuedePedirElCodigo429LaTarjetaMuestraLaEspera() = runTest {
        repo.login = noVerificado
        repo.pedirVerificacion = fallo(429, "Hiciste demasiadas solicitudes desde esta conexión. Intenta más tarde", retryAfter = "120")
        vm.login(lucia, "Boti2026!") { entro++ }
        runCurrent()
        val estado = vm.verificacion!!
        assertEquals("Hiciste demasiadas solicitudes desde esta conexión. Intenta más tarde.", estado.errorGeneral)
        assertEquals(120, estado.espera.segundos)
    }

    @Test
    fun codigoCorrectoRepiteElLoginYEntraSolo() = runTest {
        repo.respuestasLogin += noVerificado
        repo.login = ResultadoApi.Exito(SESION)
        vm.login(lucia, "Boti2026!") { entro++ }
        runCurrent()
        vm.cambiarCodigo("483920") { entro++ }
        avanzar(900)
        assertEquals(FaseCodigo.FIJA, vm.verificacion!!.fase)
        avanzar(1_400)
        assertEquals(1, entro)
        assertEquals(SESION, repo.sesionGuardada)
        assertEquals("Boti2026!", repo.ultimaPassword)
        assertEquals(
            listOf("login:$lucia", "verificacion/codigo:$lucia", "verificacion/confirmar:$lucia", "login:$lucia"),
            repo.llamadas
        )
    }

    @Test
    fun codigoIncorrectoNoRepiteElLogin() = runTest {
        repo.login = noVerificado
        repo.confirmar = fallo(400, "El código no es válido o venció. Pide uno nuevo")
        vm.login(lucia, "Boti2026!") { entro++ }
        runCurrent()
        vm.cambiarCodigo("000000") { entro++ }
        avanzar(900)
        assertEquals(FaseCodigo.FALLA, vm.verificacion!!.fase)
        avanzar(1_250)
        val estado = vm.verificacion!!
        assertEquals(FaseCodigo.ESCRIBIENDO, estado.fase)
        assertEquals("El código no es válido o venció. Pide uno nuevo.", estado.errorCodigo)
        assertEquals("", estado.codigo)
        assertEquals(0, entro)
        assertEquals(1, repo.llamadas.count { it.startsWith("login") })
    }

    @Test
    fun siElLoginFallaTrasVerificarVuelveAlFormulario() = runTest {
        repo.respuestasLogin += noVerificado
        repo.login = fallo(403, "Tu cuenta fue dada de baja")
        vm.login(lucia, "Boti2026!") { entro++ }
        runCurrent()
        vm.confirmarCodigoConCompleto("483920")
        avanzar(2_300)
        assertNull(vm.verificacion)
        assertEquals("Tu cuenta fue dada de baja.", vm.errorMessage)
        assertEquals(0, entro)
    }

    @Test
    fun reenviarPideOtroCodigoDeVerificacion() = runTest {
        repo.login = noVerificado
        vm.login(lucia, "Boti2026!") { entro++ }
        runCurrent()
        avanzar(60_000)
        vm.reenviarCodigo()
        runCurrent()
        assertEquals("Te enviamos un código nuevo.", vm.verificacion!!.mensaje)
        assertEquals(2, repo.llamadas.count { it.startsWith("verificacion/codigo") })
    }

    @Test
    fun volverAlInicioDeSesionOlvidaLaVerificacion() = runTest {
        repo.login = noVerificado
        vm.login(lucia, "Boti2026!") { entro++ }
        runCurrent()
        vm.cancelarVerificacion()
        assertNull(vm.verificacion)
        assertTrue(repo.llamadas.none { it.startsWith("verificacion/confirmar") })
    }

    private fun AuthViewModel.confirmarCodigoConCompleto(codigo: String) {
        verificacion!!.cambiar(codigo)
        confirmarCodigo { entro++ }
    }
}
