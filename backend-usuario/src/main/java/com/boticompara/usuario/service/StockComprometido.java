package com.boticompara.usuario.service;

/** Mensaje del 409 de stock comprometido, exacto según API.md, sección 4 (singular o plural). */
final class StockComprometido {

    private StockComprometido() {
    }

    static String mensaje(long n) {
        String unidades = n == 1 ? "1 unidad comprometida" : n + " unidades comprometidas";
        return "Tienes " + unidades + " en pedidos confirmados o listos para recoger; el stock no puede ser menor";
    }
}