package com.boticompara.usuario.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** H37: porcentaje de ahorro, con 1 decimal y solo si es positivo. */
class AhorroTest {

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void alternativaMasBarata_calculaElPorcentaje() {
        assertEquals(d("20.0"), Ahorro.porcentaje(d("10.00"), d("8.00")));
    }

    @Test
    void redondeaAUnDecimal() {
        // (3.00 - 2.50) / 3.00 * 100 = 16.666...
        assertEquals(d("16.7"), Ahorro.porcentaje(d("3.00"), d("2.50")));
    }

    @Test
    void ahorroPequenoPeroPositivo_seMuestra() {
        assertEquals(d("0.1"), Ahorro.porcentaje(d("10.00"), d("9.99")));
    }

    @Test
    void mismoPrecio_esNull() {
        assertNull(Ahorro.porcentaje(d("10.00"), d("10.00")));
    }

    @Test
    void alternativaMasCara_esNull() {
        assertNull(Ahorro.porcentaje(d("10.00"), d("12.00")));
    }

    @Test
    void ahorroQueRedondeaACero_esNull() {
        // 0.0001 % redondea a 0.0
        assertNull(Ahorro.porcentaje(d("10000.00"), d("9999.99")));
    }

    @Test
    void datosInvalidos_esNull() {
        assertNull(Ahorro.porcentaje(null, d("5.00")));
        assertNull(Ahorro.porcentaje(d("5.00"), null));
        assertNull(Ahorro.porcentaje(d("0.00"), d("5.00")));
    }
}
