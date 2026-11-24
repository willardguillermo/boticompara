package com.boticompara.usuario.limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Cuenta usos por clave (una IP, un usuario) en una ventana fija: como máximo {@code maximo} usos cada
 * {@code ventana}. Vive en memoria, como el de SEC-05: se pierde si la API reinicia, y con un solo proceso alcanza.
 * Para que el mapa no crezca sin tope, al llegar a {@link #MAX_CLAVES} se borran las ventanas ya vencidas y, si
 * aun así sigue lleno, se vacía (es preferible olvidar contadores a dejar sin memoria al servidor).
 */
public class LimitadorPorClave {

    static final int MAX_CLAVES = 10_000;

    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;
    private final Map<String, Uso> usos = new HashMap<>();

    private static final class Uso {
        private Instant inicio;
        private int cantidad;
    }

    public LimitadorPorClave(int maximo, Duration ventana, Clock reloj) {
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    /**
     * Si la clave todavía tiene cupo, lo gasta y devuelve 0. Si no, no gasta nada y devuelve los segundos que
     * faltan para que se libere (como mínimo 1), para el header Retry-After.
     */
    public synchronized long consumir(String clave) {
        Instant ahora = reloj.instant();
        Uso uso = usos.get(clave);
        if (uso == null || vencida(uso, ahora)) {
            limpiarSiHaceFalta(ahora);
            uso = new Uso();
            uso.inicio = ahora;
            usos.put(clave, uso);
        }
        if (uso.cantidad >= maximo) {
            return segundos(Duration.between(ahora, uso.inicio.plus(ventana)));
        }
        uso.cantidad++;
        return 0;
    }

    /** Devuelve un uso gastado con {@link #consumir}, por ejemplo si la operación terminó fallando. */
    public synchronized void devolver(String clave) {
        Uso uso = usos.get(clave);
        if (uso != null && uso.cantidad > 0) {
            uso.cantidad--;
        }
    }

    /** Para las pruebas. */
    synchronized int claves() {
        return usos.size();
    }

    private boolean vencida(Uso uso, Instant ahora) {
        return !ahora.isBefore(uso.inicio.plus(ventana));
    }

    private void limpiarSiHaceFalta(Instant ahora) {
        if (usos.size() < MAX_CLAVES) {
            return;
        }
        usos.values().removeIf(u -> vencida(u, ahora));
        if (usos.size() >= MAX_CLAVES) {
            usos.clear();
        }
    }

    /** Segundos completos que faltan, redondeando hacia arriba y como mínimo 1. */
    static long segundos(Duration falta) {
        return Math.max(1, (falta.toMillis() + 999) / 1000);
    }
}
