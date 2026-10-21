package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.ConsultaRucClient.ResultadoSunat;
import com.boticompara.usuario.service.ConsultaRucClient.Tipo;
import com.boticompara.usuario.util.RucValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H2: valida el RUC localmente y, si pasa, lo verifica en SUNAT.
 * Reglas: no existe, estado distinto de ACTIVO o condición distinta de HABIDO → no válido.
 * Si SUNAT no está disponible, se acepta con la validación local y queda un warning en el log.
 * Guarda 24 h en memoria los resultados de SUNAT (nunca los "no disponible") para cuidar la cuota.
 */
@Slf4j
@Service
public class RucSunatService {

    static final Duration VIGENCIA_CACHE = Duration.ofHours(24);
    private static final int MAX_CACHE = 1000;

    private final ConsultaRucClient cliente;
    private final Clock reloj;
    private final Map<String, EnCache> cache = new ConcurrentHashMap<>();

    private record EnCache(ResultadoSunat resultado, Instant guardado) {
    }

    @Autowired
    public RucSunatService(ConsultaRucClient cliente) {
        this(cliente, Clock.systemUTC());
    }

    /** Para los tests: permite mover el reloj y probar la caché. */
    RucSunatService(ConsultaRucClient cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
    }

    /** Solo validación local (para validar-ruc sin sesión): no gasta cuota. */
    public RucResponse verificarLocal(String ruc) {
        return RucValidator.validar(ruc);
    }

    /** Validación local y, si pasa, SUNAT. Nunca lanza excepción por el RUC: devuelve valido=false con el motivo. */
    public RucResponse verificar(String ruc) {
        RucResponse local = RucValidator.validar(ruc);
        if (!local.valido()) {
            return local;
        }
        ResultadoSunat r = consultar(ruc);
        return switch (r.tipo()) {
            case NO_DISPONIBLE -> new RucResponse(ruc, true, "RUC válido (no se pudo consultar SUNAT)",
                    null, null, null, "LOCAL", null, null);
            case NO_EXISTE -> new RucResponse(ruc, false, "El RUC no existe en SUNAT",
                    null, null, null, "SUNAT", null, null);
            case ENCONTRADO -> {
                String motivo = motivoRechazo(r);
                yield new RucResponse(ruc, motivo == null, motivo == null ? "RUC válido" : motivo,
                        r.razonSocial(), r.estado(), r.condicion(), "SUNAT", r.direccion(), r.distrito());
            }
        };
    }

    /** Para registrar o corregir una botica: 400 si el RUC no sirve. */
    public RucResponse exigirValido(String ruc) {
        RucResponse r = verificar(ruc);
        if (!r.valido()) {
            throw ApiException.badRequest(r.mensaje());
        }
        return r;
    }

    private ResultadoSunat consultar(String ruc) {
        Instant ahora = reloj.instant();
        EnCache guardado = cache.get(ruc);
        if (guardado != null && ahora.isBefore(guardado.guardado().plus(VIGENCIA_CACHE))) {
            return guardado.resultado();
        }
        ResultadoSunat r = cliente.consultar(ruc);
        if (r.tipo() == Tipo.NO_DISPONIBLE) {
            log.warn("No se pudo verificar el RUC {} en SUNAT: se acepta solo con la validación local", ruc);
            return r;
        }
        if (cache.size() >= MAX_CACHE) {
            cache.clear();
        }
        cache.put(ruc, new EnCache(r, ahora));
        return r;
    }

    private static String motivoRechazo(ResultadoSunat r) {
        if (!"ACTIVO".equalsIgnoreCase(r.estado())) {
            return "El RUC no está activo en SUNAT (estado: " + r.estado() + ")";
        }
        if (!"HABIDO".equalsIgnoreCase(r.condicion())) {
            return "El RUC no está habido en SUNAT (condición: " + r.condicion() + ")";
        }
        return null;
    }
}