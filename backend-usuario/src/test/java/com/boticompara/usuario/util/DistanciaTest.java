package com.boticompara.usuario.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** H27: valores de referencia calculados aparte con la misma fórmula. */
class DistanciaTest {

    private static final double TOLERANCIA_KM = 0.01;

    @Test
    void mismoPunto_esCero() {
        assertEquals(0.0, Distancia.km(-11.9425, -76.699, -11.9425, -76.699), TOLERANCIA_KM);
    }

    @Test
    void unGradoDeLatitud_esUnos111Km() {
        assertEquals(111.19, Distancia.km(0, 0, 1, 0), TOLERANCIA_KM);
    }

    @Test
    void plazaDeArmasDeLimaAChosica() {
        assertEquals(39.14, Distancia.km(-12.046374, -77.042793, -11.9425, -76.699), TOLERANCIA_KM);
    }

    @Test
    void distanciaCorta_unaCentesimaDeGradoDeLongitudEnChosica() {
        assertEquals(1.09, Distancia.km(-11.9425, -76.699, -11.9425, -76.689), TOLERANCIA_KM);
    }

    @Test
    void esSimetrica() {
        assertEquals(Distancia.km(-12.0464, -77.0428, -11.9425, -76.699),
                Distancia.km(-11.9425, -76.699, -12.0464, -77.0428), 1e-9);
    }

    @Test
    void puntosOpuestos_esMediaVueltaAlMundo() {
        assertEquals(20015.09, Distancia.km(0, 0, 0, 180), TOLERANCIA_KM);
    }
}