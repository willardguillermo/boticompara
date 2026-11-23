package com.boticompara.usuario.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SEC-05 (API.md, POST /auth/login): 5 fallos por correo e IP en 15 minutos bloquean 15 minutos.
 * Contador en memoria: se pierde si la API reinicia; con un solo proceso es suficiente para la demo.
 */
@Component
public class LimitadorLogin {

    static final int MAX_FALLOS = 5;
    static final Duration VENTANA = Duration.ofMinutes(15);
    static final Duration BLOQUEO = Duration.ofMinutes(15);
    private static final int MAX_CLAVES = 10_000;

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    private static final class Registro {
        int fallos;
        Instant inicioVentana;
        Instant bloqueadoHasta;
    }

    private static String clave(String correo, String ip) {
        return correo.trim().toLowerCase(Locale.ROOT) + "|" + ip;
    }

    /** Lanza LimiteLoginException si esa combinación de correo e IP está bloqueada. */
    public void verificarNoBloqueado(String correo, String ip) {
        Registro r = registros.get(clave(correo, ip));
        if (r == null || r.bloqueadoHasta == null) {
            return;
        }
        Instant ahora = Instant.now();
        if (r.bloqueadoHasta.isAfter(ahora)) {
            long segundos = Duration.between(ahora, r.bloqueadoHasta).getSeconds() + 1;
            throw new LimiteLoginException("Demasiados intentos fallidos. Espera 15 minutos", segundos);
        }
    }

    /** Cuenta un intento fallido. Al quinto dentro de la ventana, bloquea 15 minutos. */
    public void registrarFallo(String correo, String ip) {
        Instant ahora = Instant.now();
        if (registros.size() > MAX_CLAVES) {
            registros.entrySet().removeIf(e -> e.getValue().bloqueadoHasta == null
                    && e.getValue().inicioVentana.plus(VENTANA).isBefore(ahora));
        }
        registros.compute(clave(correo, ip), (k, r) -> {
            if (r == null || r.inicioVentana.plus(VENTANA).isBefore(ahora)) {
                r = new Registro();
                r.inicioVentana = ahora;
            }
            r.fallos++;
            if (r.fallos >= MAX_FALLOS) {
                r.bloqueadoHasta = ahora.plus(BLOQUEO);
                r.fallos = 0;
            }
            return r;
        });
    }

    /** Un login correcto borra el contador de esa combinación. */
    public void limpiar(String correo, String ip) {
        registros.remove(clave(correo, ip));
    }
}