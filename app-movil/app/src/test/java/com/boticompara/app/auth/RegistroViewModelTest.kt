package com.boticompara.app.auth

import com.boticompara.app.data.remote.dto.RegistroRequest
import com.boticompara.app.data.remote.dto.TokenVerificacionResponse
import com.boticompara.app.data.repository.ResultadoApi
import com.boticompara.app.ui.auth.PasoRegistro
import com.boticompara.app.ui.auth.RegistroViewModel
import com.boticompara.app.ui.auth.codigo.FaseCodigo
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistroViewModelTest : PruebaConMain() {

    private val repo = AccesoFalso()
    private val correo = "carlos@boticompara.nissidev.dev"

    private fun TestScope.enPasoCodigo(): RegistroViewModel {
        val vm = RegistroViewModel(repo)
        vm.cambiarCorreo("  Carlos@BotiCompara.nissidev.dev ")
        vm.pedirCodigo()
        runCurrent()
        return vm
    }

    private fun TestScope.enPasoCuenta(): RegistroViewModel {
        val vm = enPasoCodigo()
        vm.cambiarCodigo("483920")   // con 6 dígitos se verifica solo
        avanzar(2_300)               // 900 de la cruz + 300 fija + 1100 del check
        assertEquals(PasoRegistro.CUENTA, vm.paso)
        return vm
    }

    private fun RegistroViewModel.llenarCuenta(password: String = "Boti2026!", telefono: String = "954321098") {
        cambiarNombre("Carlos Ramírez")
        cambiarPassword(password)
        cambiarConfirmar(password)
        cambiarTelefono(telefono)
    }

    @Test
    fun elTokenNoApareceAlImprimirLasPeticiones() {
        val request = RegistroRequest(TOKEN, "Carlos", "carlos@boticompara.nissidev.dev", "Boti2026!")
        assertFalse(request.toString().contains(TOKEN))
        assertFalse(request.toString().contains("Boti2026!"))
        assertFalse(TokenVerificacionResponse(TOKEN).toString().contains(TOKEN))
    }

    // ---------- Paso 1: correo ----------

    @Test
    fun correoInvalidoNoLlamaALaApi() = runTest {
        val vm = RegistroViewModel(repo)
        vm.cambiarCorreo("carlos@")
        vm.pedirCodigo()
        runCurrent()
        assertEquals("Ingresa un correo válido.", vm.errorCorreo)
        assertTrue(repo.llamadas.isEmpty())
    }

    @Test
    fun codigoEnviadoPasaAlCodigoConElCorreoLimpioYLaEspera() = runTest {
        val vm = enPasoCodigo()
        assertEquals(PasoRegistro.CODIGO, vm.paso)
        assertEquals(correo, vm.correoVerificado)
        assertEquals(listOf("registro/codigo:$correo"), repo.llamadas)
        assertEquals(60, vm.codigo.espera.segundos)
        assertFalse(vm.tieneToken)
    }

    @Test
    fun correoConCuenta409OfreceIniciarSesion() = runTest {
        repo.pedirRegistro = fallo(409, "El correo ya está registrado")
        val vm = enPasoCodigoSinAvanzar()
        assertEquals(PasoRegistro.CORREO, vm.paso)
        assertEquals("El correo ya está registrado.", vm.errorCorreo)
        assertTrue(vm.correoYaRegistrado)
        vm.cambiarCorreo("otro@correo.pe")
        assertFalse(vm.correoYaRegistrado)
    }

    @Test
    fun limite429MuestraLaCuentaRegresivaDelRetryAfter() = runTest {
        repo.pedirRegistro = fallo(429, "Espera un momento antes de pedir otro código", retryAfter = "38")
        val vm = enPasoCodigoSinAvanzar()
        assertEquals(PasoRegistro.CORREO, vm.paso)
        assertEquals("Espera un momento antes de pedir otro código.", vm.aviso)
        assertEquals(38, vm.esperaPedir.segundos)

        // Mientras espera, el botón no vuelve a pedir
        vm.pedirCodigo()
        runCurrent()
        assertEquals(1, repo.llamadas.size)

        avanzar(38_000)
        assertFalse(vm.esperaPedir.activa)
        repo.pedirRegistro = OK
        vm.pedirCodigo()
        runCurrent()
        assertEquals(PasoRegistro.CODIGO, vm.paso)
    }

    @Test
    fun noSePudoEnviarElCorreo503() = runTest {
        repo.pedirRegistro = fallo(503, "No pudimos enviar el correo. Intenta en unos minutos")
        val vm = enPasoCodigoSinAvanzar()
        assertEquals(PasoRegistro.CORREO, vm.paso)
        assertEquals("No pudimos enviar el correo. Intenta en unos minutos.", vm.aviso)
        assertFalse(vm.esperaPedir.activa)
    }

    @Test
    fun formatoInvalido400VaEnElCampo() = runTest {
        repo.pedirRegistro = fallo(400, "El correo no tiene un formato válido")
        val vm = enPasoCodigoSinAvanzar()
        assertEquals("El correo no tiene un formato válido.", vm.errorCorreo)
    }

    @Test
    fun sinConexion() = runTest {
        repo.pedirRegistro = ResultadoApi.SinConexion
        val vm = enPasoCodigoSinAvanzar()
        assertEquals("No se pudo conectar con el servidor.", vm.aviso)
    }

    private fun TestScope.enPasoCodigoSinAvanzar() = enPasoCodigo()

    // ---------- Paso 2: código ----------

    @Test
    fun codigoCorrectoGuardaElTokenSoloEnMemoriaYPasaALaCuenta() = runTest {
        val vm = enPasoCuenta()
        assertTrue(vm.tieneToken)
        assertEquals("483920", repo.ultimoCodigo)
        assertEquals(listOf("registro/codigo:$correo", "registro/verificar:$correo"), repo.llamadas)
    }

    @Test
    fun codigoIncorrecto400SeQuedaEnElCodigoConLasCasillasVacias() = runTest {
        repo.verificarRegistro = fallo(400, "El código no es válido o venció. Pide uno nuevo")
        val vm = enPasoCodigo()
        vm.cambiarCodigo("111111")
        avanzar(900)
        assertEquals(FaseCodigo.FALLA, vm.codigo.fase)
        avanzar(1_250)
        assertEquals(PasoRegistro.CODIGO, vm.paso)
        assertEquals("El código no es válido o venció. Pide uno nuevo.", vm.codigo.errorCodigo)
        assertEquals("", vm.codigo.codigo)
        assertFalse(vm.tieneToken)
    }

    @Test
    fun reenviarCon429MuestraLaEspera() = runTest {
        val vm = enPasoCodigo()
        avanzar(60_000)
        repo.pedirRegistro = fallo(429, "Pediste demasiados códigos para este correo. Intenta más tarde", retryAfter = "1800")
        vm.reenviarCodigo()
        runCurrent()
        assertEquals("Pediste demasiados códigos para este correo. Intenta más tarde.", vm.codigo.errorGeneral)
        assertEquals(1_800, vm.codigo.espera.segundos)
    }

    @Test
    fun cambiarCorreoOlvidaElToken() = runTest {
        val vm = enPasoCuenta()
        vm.volverAlCorreo()
        assertEquals(PasoRegistro.CORREO, vm.paso)
        assertFalse(vm.tieneToken)
    }

    // ---------- Paso 3: cuenta ----------

    @Test
    fun crearCuentaEnviaElTokenYEntraConLaSesion() = runTest {
        val vm = enPasoCuenta()
        vm.llenarCuenta()
        vm.crearCuenta()
        runCurrent()
        val enviado = repo.ultimoRegistro!!
        assertEquals(TOKEN, enviado.tokenVerificacion)
        assertEquals(correo, enviado.correo)
        assertEquals("COMPRADOR", enviado.rol)
        assertEquals("Carlos Ramírez", enviado.nombre)
        assertEquals("954321098", enviado.telefono)
        assertEquals(SESION, repo.sesionGuardada)
        assertTrue(vm.registrado)
        assertFalse(vm.tieneToken)
        assertEquals("", vm.password)
    }

    @Test
    fun telefonoVacioSeEnviaComoNull() = runTest {
        val vm = enPasoCuenta()
        vm.llenarCuenta(telefono = "")
        vm.crearCuenta()
        runCurrent()
        assertNull(repo.ultimoRegistro!!.telefono)
    }

    @Test
    fun validaLaCuentaAntesDeEnviar() = runTest {
        val vm = enPasoCuenta()
        vm.cambiarPassword("corta")
        vm.cambiarConfirmar("otra")
        vm.cambiarTelefono("95-43")
        vm.crearCuenta()
        runCurrent()
        assertEquals("Ingresa tu nombre completo.", vm.errorNombre)
        assertEquals("La contraseña debe tener al menos 8 caracteres.", vm.errorPassword)
        assertEquals("Las contraseñas no coinciden.", vm.errorConfirmar)
        assertEquals("Usa solo números (6 a 15 dígitos).", vm.errorTelefono)
        assertNull(repo.ultimoRegistro)
    }

    @Test
    fun tokenVencidoVuelveAlCorreoConUnMensajeClaro() = runTest {
        repo.registrar = fallo(400, "Tu verificación del correo venció. Vuelve a pedir un código")
        val vm = enPasoCuenta()
        vm.llenarCuenta()
        vm.crearCuenta()
        runCurrent()
        assertEquals(PasoRegistro.CORREO, vm.paso)
        assertEquals("Tu verificación del correo venció. Vuelve a pedir un código.", vm.aviso)
        assertFalse(vm.tieneToken)
        assertFalse(vm.registrado)
        assertNull(repo.sesionGuardada)
        assertEquals("  Carlos@BotiCompara.nissidev.dev ", vm.correo)   // el correo queda escrito
    }

    @Test
    fun correoRegistradoMientrasTanto409VuelveAlCorreo() = runTest {
        repo.registrar = fallo(409, "El correo ya está registrado")
        val vm = enPasoCuenta()
        vm.llenarCuenta()
        vm.crearCuenta()
        runCurrent()
        assertEquals(PasoRegistro.CORREO, vm.paso)
        assertEquals("El correo ya está registrado.", vm.errorCorreo)
        assertTrue(vm.correoYaRegistrado)
        assertFalse(vm.tieneToken)
    }

    @Test
    fun errorDeLaContrasena400VaEnElCampo() = runTest {
        repo.registrar = fallo(400, "La contraseña debe tener entre 8 y 72 caracteres")
        val vm = enPasoCuenta()
        vm.llenarCuenta()
        vm.crearCuenta()
        runCurrent()
        assertEquals(PasoRegistro.CUENTA, vm.paso)
        assertEquals("La contraseña debe tener entre 8 y 72 caracteres.", vm.errorPassword)
        assertTrue(vm.tieneToken)   // el token sigue sirviendo para corregir y reenviar
    }

    @Test
    fun otro400SeMuestraArriba() = runTest {
        repo.registrar = fallo(400, "Un comprador no registra botica")
        val vm = enPasoCuenta()
        vm.llenarCuenta()
        vm.crearCuenta()
        runCurrent()
        assertEquals("Un comprador no registra botica.", vm.aviso)
        assertEquals(PasoRegistro.CUENTA, vm.paso)
    }

    @Test
    fun crearCuentaSinConexionNoPierdeLoEscrito() = runTest {
        repo.registrar = ResultadoApi.SinConexion
        val vm = enPasoCuenta()
        vm.llenarCuenta()
        vm.crearCuenta()
        runCurrent()
        assertEquals("No se pudo conectar con el servidor.", vm.aviso)
        assertEquals("Boti2026!", vm.password)
        assertTrue(vm.tieneToken)
    }
}
