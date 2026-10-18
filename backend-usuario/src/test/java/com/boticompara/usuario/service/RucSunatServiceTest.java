package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.error.ApiException;
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
        service = new RucSunatService(cliente, reloj);
    }

    // ---------- Reglas ----------

    @Test
    void rucMalEscrito_noConsultaSunat() {
        RucResponse r = service.verificar("20131312954");

        assertFalse(r.valido());
        assertEquals("LOCAL", r.fuente());
        assertEquals(0, cliente.llamadas);
    }

    @Test
    void activoYHabido_esValidoConLosDatosDeSunat() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        RucResponse r = service.verificar(RUC);

        assertTrue(r.valido());
        assertEquals("RUC válido", r.mensaje());
        assertEquals("SUNAT", r.razonSocial());
        assertEquals("ACTIVO", r.estado());
        assertEquals("HABIDO", r.condicion());
        assertEquals("SUNAT", r.fuente());
    }

    @Test
    void rucQueNoExiste_noEsValido() {
        cliente.respuesta = ResultadoSunat.noExiste();

        RucResponse r = service.verificar(RUC);

        assertFalse(r.valido());
        assertEquals("El RUC no existe en SUNAT", r.mensaje());
        assertEquals("SUNAT", r.fuente());
    }

    @Test
    void estadoDistintoDeActivo_noEsValido() {
        cliente.respuesta = ResultadoSunat.encontrado("EMPRESA ZZ S.A.C.", "BAJA DE OFICIO", "HABIDO");

        RucResponse r = service.verificar(RUC);

        assertFalse(r.valido());
        assertEquals("El RUC no está activo en SUNAT (estado: BAJA DE OFICIO)", r.mensaje());
        assertEquals("EMPRESA ZZ S.A.C.", r.razonSocial());
    }

    @Test
    void condicionDistintaDeHabido_noEsValido() {
        cliente.respuesta = ResultadoSunat.encontrado("EMPRESA ZZ S.A.C.", "ACTIVO", "NO HABIDO");

        RucResponse r = service.verificar(RUC);

        assertFalse(r.valido());
        assertEquals("El RUC no está habido en SUNAT (condición: NO HABIDO)", r.mensaje());
    }

    @Test
    void sunatNoDisponible_seAceptaConLaValidacionLocal() {
        RucResponse r = service.verificar(RUC);

        assertTrue(r.valido());
        assertEquals("RUC válido (no se pudo consultar SUNAT)", r.mensaje());
        assertEquals("LOCAL", r.fuente());
        assertNull(r.razonSocial());
    }

    @Test
    void verificarLocal_nuncaConsultaSunat() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        RucResponse r = service.verificarLocal(RUC);

        assertTrue(r.valido());
        assertEquals("LOCAL", r.fuente());
        assertEquals(0, cliente.llamadas);
    }

    // ---------- exigirValido (POST y PUT de botica) ----------

    @Test
    void exigirValido_rucRechazadoPorSunat_lanza400ConElMotivo() {
        cliente.respuesta = ResultadoSunat.noExiste();

        ApiException ex = assertThrows(ApiException.class, () -> service.exigirValido(RUC));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("El RUC no existe en SUNAT", ex.getMessage());
    }

    @Test
    void exigirValido_rucValido_devuelveLaRazonSocial() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        assertEquals("SUNAT", service.exigirValido(RUC).razonSocial());
    }

    // ---------- Caché ----------

    @Test
    void cache_laSegundaConsultaNoLlamaASunat() {
        cliente.respuesta = ResultadoSunat.encontrado("SUNAT", "ACTIVO", "HABIDO");

        service.verificar(RUC);
        service.verificar(RUC);

        assertEquals(1, cliente.llamadas);
    }

    @Test
    void cache_venceA_Las24Horas() {
        cliente.respuesta = ResultadoSunat.noExiste();
        service.verificar(RUC);

        reloj.ahora = reloj.ahora.plus(Duration.ofHours(24)).plusSeconds(1);
        service.verificar(RUC);

        assertEquals(2, cliente.llamadas);
    }

    @Test
    void cache_noGuardaLosNoDisponible() {
        service.verificar(RUC);
        service.verificar(RUC);

        assertEquals(2, cliente.llamadas);
    }
}
