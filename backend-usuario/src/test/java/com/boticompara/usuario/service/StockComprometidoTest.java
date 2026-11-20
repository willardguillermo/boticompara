package com.boticompara.usuario.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockComprometidoTest {

    @Test
    void singularParaUnaUnidad() {
        assertEquals("Tienes 1 unidad comprometida en pedidos confirmados o listos para recoger; el stock no puede ser menor",
                StockComprometido.mensaje(1));
    }

    @Test
    void pluralParaVarias() {
        assertEquals("Tienes 5 unidades comprometidas en pedidos confirmados o listos para recoger; el stock no puede ser menor",
                StockComprometido.mensaje(5));
    }
}