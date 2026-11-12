package com.boticompara.usuario.chat;

import com.boticompara.usuario.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * H68: límites de preguntas del chat. Por usuario: por defecto 3 por minuto y 15 por día (429). Global, entre
 * todos los usuarios del backend: por defecto 150 por día (503), porque la cuota gratis de Gemini es una sola
 * por proyecto y unos pocos usuarios podrían agotarla para todos. El día cambia a las 00:00 de Lima.
 * El conteo se lleva en memoria y se reinicia con el servidor: aceptable para el curso, no hace falta una tabla.
 */
@Component
public class LimitadorChat {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final String MENSAJE_MINUTO = "Hiciste muchas preguntas seguidas. Espera un minuto y vuelve a intentar";
    static final String MENSAJE_DIA = "Llegaste al límite de preguntas de hoy. Vuelve mañana";
    static final String MENSAJE_GLOBAL = "El asistente alcanzó su límite de hoy. Intenta mañana.";
    private static final Duration MINUTO = Duration.ofMinutes(1);

    private final int porMinuto;
    private final int porDia;
    private final int globalPorDia;
    private final Clock reloj;
    private final Map<Long, Uso> usos = new HashMap<>();
    /** Preguntas aceptadas hoy entre todos los usuarios. */
    private LocalDate diaGlobal;
    private int globalEnElDia;

    /** Preguntas aceptadas de un usuario: las del último minuto y cuántas lleva en el día. */
    private static final class Uso {
        private final Deque<Instant> recientes = new ArrayDeque<>();
        private LocalDate dia;
        private int enElDia;
    }

    @Autowired
    public LimitadorChat(@Value("${chat.limite.por-minuto:3}") int porMinuto,
                         @Value("${chat.limite.por-dia:15}") int porDia,
                         @Value("${chat.limite.global-por-dia:150}") int globalPorDia) {
        this(porMinuto, porDia, globalPorDia, Clock.systemUTC());
    }

    LimitadorChat(int porMinuto, int porDia, int globalPorDia, Clock reloj) {
        this.porMinuto = porMinuto;
        this.porDia = porDia;
        this.globalPorDia = globalPorDia;
        this.reloj = reloj;
    }

    /**
     * Cuenta una pregunta del usuario. Se llama después de validar el pedido, así los 400 y 401 no cuentan.
     * Primero se revisa el límite del usuario (429) y después el tope global (503). Una pregunta rechazada
     * por cualquiera de los dos no cuenta en ninguno.
     *
     * @throws LimiteChatException si el usuario ya llegó a su límite del minuto o del día
     * @throws ApiException        503 si el backend ya llegó a su tope global del día
     */
    public synchronized void registrar(Long usuarioId) {
        Instant ahora = reloj.instant();
        LocalDate hoy = LocalDate.ofInstant(ahora, LIMA);
        Uso uso = usos.computeIfAbsent(usuarioId, id -> new Uso());
        if (!hoy.equals(uso.dia)) {
            uso.dia = hoy;
            uso.enElDia = 0;
        }
        Instant haceUnMinuto = ahora.minus(MINUTO);
        while (!uso.recientes.isEmpty() && !uso.recientes.peekFirst().isAfter(haceUnMinuto)) {
            uso.recientes.pollFirst();
        }
        if (uso.enElDia >= porDia) {
            Instant medianoche = hoy.plusDays(1).atStartOfDay(LIMA).toInstant();
            throw new LimiteChatException(MENSAJE_DIA, segundos(Duration.between(ahora, medianoche)));
        }
        if (uso.recientes.size() >= porMinuto) {
            Instant libre = uso.recientes.isEmpty() ? ahora.plus(MINUTO) : uso.recientes.peekFirst().plus(MINUTO);
            throw new LimiteChatException(MENSAJE_MINUTO, segundos(Duration.between(ahora, libre)));
        }
        if (!hoy.equals(diaGlobal)) {
            diaGlobal = hoy;
            globalEnElDia = 0;
        }
        if (globalEnElDia >= globalPorDia) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MENSAJE_GLOBAL);
        }
        uso.recientes.addLast(ahora);
        uso.enElDia++;
        globalEnElDia++;
    }

    /** Segundos completos que faltan, redondeando hacia arriba y como mínimo 1. */
    private static long segundos(Duration falta) {
        return Math.max(1, (falta.toMillis() + 999) / 1000);
    }
}
