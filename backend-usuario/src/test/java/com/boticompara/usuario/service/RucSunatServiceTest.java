package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.limite.CuotaSunat;
import com.boticompara.usuario.limite.LimiteExcedidoException;
import com.boticompara.usuario.service.ConsultaRucClient.ResultadoSunat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H2: reglas de SUNAT y caché de 24 h. Usa un cliente falso: nunca llama al servicio real. */
class RucSunatServiceTest {

    private static final String RUC = "20131312955";   // dígito verificador válido
    private static final String OTRO_RUC = "20601234565";
    private static final Long USUARIO = 7L;

    /** Cliente simulado: devuelve la respuesta configurada y cuenta las llamadas. */
    private static final class ClienteFalso implements ConsultaRucClient {
        ResultadoSunat respuesta = ResultadoSunat.noDisponible();
        int llamadas;

        @Override
        public ResultadoSunat consultar(String ruc) {
            llamadas++;
            return respuesta;
        }
    }

    /** Reloj que se puede adelantar para probar el vencimiento de la caché. */
    private static final class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-10-17T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private ClienteFalso cliente;
    private RelojMovil reloj;
    private RucSunatService service;

    @BeforeEach
    void preparar() {
        cliente = new ClienteFalso();
        reloj = new RelojMovil();
        service = new RucSunatService(cliente, new CuotaSunat(1000, 1000, reloj), reloj);
    }

    // ---------- Reglas ----------

    @Test
    void rucMalEscrito_noConsultaSunat() {
        RucResponse r = service.consultarParaValidar("20131312954", USUARIO);

        assertFalse(r.valido());
        assertEquals("LOCAL", r.fuente());
        assertEquals(0, cliente.llamadas);
    }

    @Test
    void activoYHabido_esValidoConLosDatosDeSunat() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        RucResponse r = service.consultarParaValidar(RUC, USUARIO);

        assertTrue(r.valido());
        assertEquals("RUC válido", r.mensaje());
        assertEquals("SUNAT", r.razonSocial());
        assertEquals("ACTIVO", r.estado());
        assertEquals("HABIDO", r.condicion());
        assertEquals("SUNAT", r.fuente());
    }

    @Test
    void encontradoConDireccion_laDevuelveConElDistrito() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO",
                "AV. GARCILASO DE LA VEGA NRO. 1472", "LIMA");

        RucResponse r = service.consultarParaValidar(RUC, USUARIO);

        assertTrue(r.valido());
        assertEquals("AV. GARCILASO DE LA VEGA NRO. 1472", r.direccion());
        assertEquals("LIMA", r.distrito());
    }

    @Test
    void encontradoSinDireccion_laDejaEnNull() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        RucResponse r = service.consultarParaValidar(RUC, USUARIO);

        assertTrue(r.valido());
        assertEquals("SUNAT", r.fuente());
        assertNull(r.direccion());
        assertNull(r.distrito());
    }

    @Test
    void rucQueNoExiste_noEsValido() {
        cliente.respuesta = ResultadoSunat.noExiste();

        RucResponse r = service.consultarParaValidar(RUC, USUARIO);

        assertFalse(r.valido());
        assertEquals("El RUC no existe en SUNAT", r.mensaje());
        assertEquals("SUNAT", r.fuente());
    }

    @Test
    void estadoDistintoDeActivo_noEsValido() {
        cliente.respuesta = ResultadoSunat.encontrado("EMPRESA ZZ S.A.C.", "BAJA DE OFICIO", "HABIDO");

        RucResponse r = service.consultarParaValidar(RUC, USUARIO);

        assertFalse(r.valido());
        assertEquals("El RUC no está activo en SUNAT (estado: BAJA DE OFICIO)", r.mensaje());
        assertEquals("EMPRESA ZZ S.A.C.", r.razonSocial());
    }

    @Test
    void condicionDistintaDeHabido_noEsValido() {
        cliente.respuesta = ResultadoSunat.encontrado("EMPRESA ZZ S.A.C.", "ACTIVO", "NO HABIDO");

        RucResponse r = service.consultarParaValidar(RUC, USUARIO);

        assertFalse(r.valido());
        assertEquals("El RUC no está habido en SUNAT (condición: NO HABIDO)", r.mensaje());
    }

    @Test
    void sunatNoDisponible_seAceptaConLaValidacionLocal() {
        RucResponse r = service.consultarParaValidar(RUC, USUARIO);

        assertTrue(r.valido());
        assertEquals("RUC válido (no se pudo consultar SUNAT)", r.mensaje());
        assertEquals("LOCAL", r.fuente());
        assertNull(r.razonSocial());
        assertNull(r.direccion());
        assertNull(r.distrito());
    }

    // ---------- exigirValido (POST y PUT de botica) ----------

    @Test
    void exigirValido_rucRechazadoPorSunat_lanza400ConElMotivo() {
        cliente.respuesta = ResultadoSunat.noExiste();

        ApiException ex = assertThrows(ApiException.class, () -> service.exigirValido(RUC, USUARIO));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("El RUC no existe en SUNAT", ex.getMessage());
    }

    @Test
    void exigirValido_rucValido_devuelveLaRazonSocial() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        assertEquals("SUNAT", service.exigirValido(RUC, USUARIO).razonSocial());
    }

    // ---------- Caché ----------

    @Test
    void cache_laSegundaConsultaNoLlamaASunat() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        service.consultarParaValidar(RUC, USUARIO);
        service.consultarParaValidar(RUC, USUARIO);

        assertEquals(1, cliente.llamadas);
    }

    @Test
    void cache_conservaLaDireccion() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO",
                "AV. GARCILASO DE LA VEGA NRO. 1472", "LIMA");

        service.consultarParaValidar(RUC, USUARIO);
        RucResponse r = service.consultarParaValidar(RUC, USUARIO);

        assertEquals(1, cliente.llamadas);
        assertEquals("AV. GARCILASO DE LA VEGA NRO. 1472", r.direccion());
        assertEquals("LIMA", r.distrito());
    }

    @Test
    void cache_venceA_Las24Horas() {
        cliente.respuesta = ResultadoSunat.noExiste();
        service.consultarParaValidar(RUC, USUARIO);

        reloj.ahora = reloj.ahora.plus(Duration.ofHours(24)).plusSeconds(1);
        service.consultarParaValidar(RUC, USUARIO);

        assertEquals(2, cliente.llamadas);
    }

    @Test
    void cache_noGuardaLosNoDisponible() {
        service.consultarParaValidar(RUC, USUARIO);
        service.consultarParaValidar(RUC, USUARIO);

        assertEquals(2, cliente.llamadas);
    }

    // ---------- Límites de consultas (CuotaSunat) ----------

    private void conCuota(int porUsuarioHora, int globalPorDia) {
        service = new RucSunatService(cliente, new CuotaSunat(porUsuarioHora, globalPorDia, reloj), reloj);
    }

    @Test
    void limiteDelUsuario_enValidarRuc_da429ConLosSegundosDeEspera() {
        conCuota(1, 100);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");
        service.consultarParaValidar(OTRO_RUC, USUARIO);

        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(20));
        LimiteExcedidoException ex = assertThrows(LimiteExcedidoException.class,
                () -> service.consultarParaValidar(RUC, USUARIO));

        assertEquals("Consultaste demasiados RUC. Espera un momento y vuelve a intentar", ex.getMessage());
        assertEquals(40 * 60, ex.getReintentarEnSegundos());
        assertEquals(1, cliente.llamadas);
    }

    @Test
    void limiteDelUsuario_lasRespuestasDeLaCacheNoGastan() {
        conCuota(2, 100);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        service.consultarParaValidar(RUC, USUARIO);
        service.consultarParaValidar(RUC, USUARIO);
        service.consultarParaValidar(RUC, USUARIO);
        RucResponse r = service.consultarParaValidar(OTRO_RUC, USUARIO);

        assertEquals("SUNAT", r.fuente());
        assertEquals(2, cliente.llamadas);
    }

    @Test
    void limiteDelUsuario_losRucMalEscritosNoGastan() {
        conCuota(1, 1);
        service.consultarParaValidar("20131312954", USUARIO);
        service.consultarParaValidar("123", USUARIO);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        assertEquals("SUNAT", service.consultarParaValidar(RUC, USUARIO).fuente());
    }

    @Test
    void limiteDelUsuario_cadaUsuarioTieneElSuyo() {
        conCuota(1, 100);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");
        service.consultarParaValidar(OTRO_RUC, USUARIO);

        assertEquals("SUNAT", service.consultarParaValidar(RUC, 8L).fuente());
    }

    @Test
    void limiteDelUsuario_seLiberaALaHora() {
        conCuota(1, 100);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");
        service.consultarParaValidar(OTRO_RUC, USUARIO);

        reloj.ahora = reloj.ahora.plus(Duration.ofHours(1));

        assertEquals("SUNAT", service.consultarParaValidar(RUC, USUARIO).fuente());
    }

    @Test
    void limiteDelUsuario_alRegistrarLaBotica_usaLaValidacionLocalSin429() {
        conCuota(1, 100);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");
        service.consultarParaValidar(OTRO_RUC, USUARIO);

        RucResponse r = service.exigirValido(RUC, USUARIO);

        assertTrue(r.valido());
        assertEquals("LOCAL", r.fuente());
        assertNull(r.razonSocial());
        assertEquals(1, cliente.llamadas);
    }

    @Test
    void topeGlobal_respondeConLaValidacionLocalSinConsultarSunat() {
        conCuota(100, 1);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");
        service.consultarParaValidar(OTRO_RUC, USUARIO);

        RucResponse r = service.consultarParaValidar(RUC, 8L);

        assertTrue(r.valido());
        assertEquals("RUC válido (no se pudo consultar SUNAT)", r.mensaje());
        assertEquals("LOCAL", r.fuente());
        assertEquals(1, cliente.llamadas);
    }

    @Test
    void topeGlobal_tambienCuentaElRegistroDeLaBotica() {
        conCuota(100, 1);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");
        service.exigirValido(OTRO_RUC, USUARIO);

        assertEquals("LOCAL", service.consultarParaValidar(RUC, 8L).fuente());
        assertEquals(1, cliente.llamadas);
    }

    @Test
    void topeGlobal_seReiniciaALaMedianocheDeLima() {
        conCuota(100, 1);
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");
        // 2026-10-17T12:00Z son las 7:00 en Lima; a las 05:00Z del día siguiente ya es otro día en Lima
        service.consultarParaValidar(OTRO_RUC, USUARIO);
        reloj.ahora = Instant.parse("2026-10-18T05:00:00Z");

        assertEquals("SUNAT", service.consultarParaValidar(RUC, 8L).fuente());
    }
}
