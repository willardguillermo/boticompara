package com.boticompara.usuario.util;

import com.boticompara.usuario.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** H60: validación del cuerpo de PUT /favoritos/{productoId}/precio-objetivo. */
class PrecioObjetivoTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static BigDecimal leer(String cuerpo) throws Exception {
        return PrecioObjetivo.leer(JSON.readTree(cuerpo));
    }

    private static void esperar400(String cuerpo, String mensaje) {
        ApiException ex = assertThrows(ApiException.class, () -> leer(cuerpo));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(mensaje, ex.getMessage());
    }

    @Test
    void numeroValido_loDevuelveConDosDecimales() throws Exception {
        assertEquals(new BigDecimal("7.50"), leer("{\"precioObjetivo\": 7.5}"));
        assertEquals(new BigDecimal("12.00"), leer("{\"precioObjetivo\": 12}"));
    }

    @Test
    void nullExplicito_quitaElObjetivo() throws Exception {
        assertNull(leer("{\"precioObjetivo\": null}"));
    }

    @Test
    void sinElCampo_responde400() {
        esperar400("{}", PrecioObjetivo.MENSAJE_FALTA);
        esperar400("{\"otro\": 5}", PrecioObjetivo.MENSAJE_FALTA);
        esperar400("[]", PrecioObjetivo.MENSAJE_FALTA);
    }

    @Test
    void ceroONegativo_responde400() {
        esperar400("{\"precioObjetivo\": 0}", PrecioObjetivo.MENSAJE_INVALIDO);
        esperar400("{\"precioObjetivo\": -3.5}", PrecioObjetivo.MENSAJE_INVALIDO);
    }

    @Test
    void masDeDosDecimales_responde400() {
        esperar400("{\"precioObjetivo\": 7.505}", PrecioObjetivo.MENSAJE_INVALIDO);
    }

    @Test
    void desde100000_responde400() {
        esperar400("{\"precioObjetivo\": 100000}", PrecioObjetivo.MENSAJE_INVALIDO);
        assertDoesNotThrow(() -> leer("{\"precioObjetivo\": 99999.99}"));
    }

    @Test
    void texto_responde400() {
        esperar400("{\"precioObjetivo\": \"7.50\"}", PrecioObjetivo.MENSAJE_INVALIDO);
    }
}