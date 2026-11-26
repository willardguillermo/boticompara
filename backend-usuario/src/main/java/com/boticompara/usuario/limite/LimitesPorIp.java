package com.boticompara.usuario.limite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Límites por IP de los endpoints públicos que envían correo (el plan de correo da 100 envíos al día):
 * POST /auth/registro/codigo, POST /auth/recuperar y POST /auth/verificacion/codigo, por defecto 5 por hora cada uno
 * (API.md, sección 1).
 * La IP es la de request.getRemoteAddr(), que con server.forward-headers-strategy=native ya es la del cliente
 * detrás del proxy de Render, igual que en el login (SEC-05).
 */
@Component
public class LimitesPorIp {

    static final String MENSAJE_REGISTRO = "Hiciste demasiados registros desde esta conexión. Intenta más tarde";
    static final String MENSAJE_RECUPERAR = "Hiciste demasiadas solicitudes desde esta conexión. Intenta más tarde";

    private final LimitadorPorClave registro;
    private final LimitadorPorClave recuperar;
    private final LimitadorPorClave verificacion;

    @Autowired
    public LimitesPorIp(@Value("${auth.limite.registro-por-ip-hora:5}") int registroPorHora,
                        @Value("${auth.limite.recuperar-por-ip-hora:5}") int recuperarPorHora,
                        @Value("${auth.limite.verificacion-por-ip-hora:5}") int verificacionPorHora) {
        this(registroPorHora, recuperarPorHora, verificacionPorHora, Clock.systemUTC());
    }

    LimitesPorIp(int registroPorHora, int recuperarPorHora, Clock reloj) {
        this(registroPorHora, recuperarPorHora, 5, reloj);
    }

    LimitesPorIp(int registroPorHora, int recuperarPorHora, int verificacionPorHora, Clock reloj) {
        this.registro = new LimitadorPorClave(registroPorHora, Duration.ofHours(1), reloj);
        this.recuperar = new LimitadorPorClave(recuperarPorHora, Duration.ofHours(1), reloj);
        this.verificacion = new LimitadorPorClave(verificacionPorHora, Duration.ofHours(1), reloj);
    }

    /**
     * Gasta un registro de esa IP; si no quedan, lanza 429. Desde H20 se gasta al pedir el código de registro
     * (POST /auth/registro/codigo), que es lo que envía el correo. Si falla, llamar a {@link #devolverRegistro}.
     */
    public void consumirRegistro(String ip) {
        lanzarSiExcede(registro.consumir(ip), MENSAJE_REGISTRO);
    }

    public void devolverRegistro(String ip) {
        registro.devolver(ip);
    }

    /** Gasta una solicitud de recuperación de esa IP, exista o no el correo; si no quedan, lanza 429. */
    public void consumirRecuperar(String ip) {
        lanzarSiExcede(recuperar.consumir(ip), MENSAJE_RECUPERAR);
    }

    /**
     * H20: gasta una solicitud de POST /auth/verificacion/codigo de esa IP, exista o no la cuenta (como recuperar);
     * si no quedan, lanza 429.
     */
    public void consumirVerificacion(String ip) {
        lanzarSiExcede(verificacion.consumir(ip), MENSAJE_RECUPERAR);
    }

    private static void lanzarSiExcede(long espera, String mensaje) {
        if (espera > 0) {
            throw new LimiteExcedidoException(mensaje, espera);
        }
    }
}
