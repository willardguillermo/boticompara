package com.boticompara.usuario.limite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Límites de consultas a SUNAT (Decolecta: 100 al mes), compartidos por validar-ruc, POST /boticas y PUT /boticas/mia
 * (API.md, sección 3). Por usuario: por defecto 5 por hora. Global, entre todos: por defecto 10 por día de Lima.
 * Solo se llama cuando de verdad se va a consultar a Decolecta: la caché y los RUC mal escritos no gastan.
 */
@Component
public class CuotaSunat {

    static final ZoneId LIMA = ZoneId.of("America/Lima");

    public enum Resultado { PERMITIDA, LIMITE_USUARIO, TOPE_GLOBAL }

    /** Resultado de pedir permiso; {@code reintentarEnSegundos} solo tiene sentido con LIMITE_USUARIO. */
    public record Permiso(Resultado resultado, long reintentarEnSegundos) {
    }

    private final LimitadorPorClave porUsuario;
    private final int globalPorDia;
    private final Clock reloj;
    private LocalDate dia;
    private int enElDia;

    @Autowired
    public CuotaSunat(@Value("${ruc.limite.por-usuario-hora:5}") int porUsuarioHora,
                      @Value("${ruc.limite.global-por-dia:10}") int globalPorDia) {
        this(porUsuarioHora, globalPorDia, Clock.systemUTC());
    }

    /** También para las pruebas: permite mover el reloj. */
    public CuotaSunat(int porUsuarioHora, int globalPorDia, Clock reloj) {
        this.porUsuario = new LimitadorPorClave(porUsuarioHora, Duration.ofHours(1), reloj);
        this.globalPorDia = globalPorDia;
        this.reloj = reloj;
    }

    /**
     * Pide permiso para una consulta. Primero el límite del usuario y después el global; si cualquiera rechaza,
     * la consulta no cuenta en ninguno.
     */
    public Permiso pedir(Long usuarioId) {
        return pedir(String.valueOf(usuarioId));
    }

    /**
     * Igual, con cualquier clave para el límite "por usuario". Durante el registro (H20) la cuenta aún no existe y la
     * clave es el token de verificación ({@code registro-<id>}): nunca choca con el id numérico de un usuario.
     */
    public synchronized Permiso pedir(String clave) {
        LocalDate hoy = LocalDate.ofInstant(reloj.instant(), LIMA);
        if (!hoy.equals(dia)) {
            dia = hoy;
            enElDia = 0;
        }
        long espera = porUsuario.consumir(clave);
        if (espera > 0) {
            return new Permiso(Resultado.LIMITE_USUARIO, espera);
        }
        if (enElDia >= globalPorDia) {
            porUsuario.devolver(clave);
            return new Permiso(Resultado.TOPE_GLOBAL, 0);
        }
        enElDia++;
        return new Permiso(Resultado.PERMITIDA, 0);
    }
}
