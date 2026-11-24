package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BoticaCreadaResponse;
import com.boticompara.usuario.dto.BoticaDetalleResponse;
import com.boticompara.usuario.dto.BoticaRequest;
import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.util.RucValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BoticaService {

    private static final String APROBADO = "APROBADO";

    private final BoticaRepository boticas;
    private final RucSunatService rucSunat;
    private final UsuarioRepository usuarios;

    /** H1, H2, H3: la botica queda PENDIENTE hasta que el admin la apruebe en Django. */
    @Transactional
    public BoticaCreadaResponse registrar(Long usuarioId, BoticaRequest r) {
        String ruc = r.ruc().trim();
        if (!RucValidator.esValido(ruc)) {
            throw ApiException.badRequest("El RUC no es válido");
        }
        validarUbicacion(r);
        if (boticas.existsByUsuarioId(usuarioId)) {
            throw ApiException.conflict("Ya tienes una botica registrada");
        }
        exigirRucLibre(ruc);
        // SUNAT al final: no se gasta cuota en registros que igual se rechazarían
        RucResponse sunat = rucSunat.exigirValido(ruc, usuarioId);
        exigirTitular(usuarioId, ruc, sunat);
        String razonSocial = razonSocial(r, sunat);
        Botica b = new Botica();
        b.setUsuarioId(usuarioId);
        aplicar(b, r, ruc, razonSocial);
        b.setEstado("PENDIENTE");
        return BoticaCreadaResponse.from(boticas.save(b));
    }

    /**
     * H7: corrige y reenvía una solicitud rechazada. Solo si el estado es RECHAZADO;
     * la botica vuelve a PENDIENTE y se borra el motivo de rechazo.
     */
    @Transactional
    public BoticaDetalleResponse corregir(Long usuarioId, BoticaRequest r) {
        Botica b = obtenerMia(usuarioId);
        if (!"RECHAZADO".equals(b.getEstado())) {
            throw ApiException.forbidden(
                    "Solo puedes corregir una botica rechazada (estado actual: " + b.getEstado() + ")");
        }
        String ruc = r.ruc().trim();
        if (!RucValidator.esValido(ruc)) {
            throw ApiException.badRequest("El RUC no es válido");
        }
        validarUbicacion(r);
        // Su botica está RECHAZADO, así que nunca choca consigo misma: aunque conserve su RUC,
        // se revisa por si otra cuenta lo acreditó mientras tanto
        exigirRucLibre(ruc);
        RucResponse sunat = rucSunat.exigirValido(ruc, usuarioId);
        exigirTitular(usuarioId, ruc, sunat);
        String razonSocial = razonSocial(r, sunat);
        aplicar(b, r, ruc, razonSocial);
        b.setEstado("PENDIENTE");
        b.setMotivoRechazo(null);
        return BoticaDetalleResponse.from(boticas.save(b));
    }

    /** H5 */
    @Transactional(readOnly = true)
    public BoticaDetalleResponse detalleMia(Long usuarioId) {
        return BoticaDetalleResponse.from(obtenerMia(usuarioId));
    }

    @Transactional(readOnly = true)
    public Botica obtenerMia(Long usuarioId) {
        return boticas.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Aún no registraste tu botica"));
    }

    /**
     * A5: el RUC solo se reserva al aprobar la botica. Varias cuentas pueden tener solicitudes PENDIENTE o
     * RECHAZADO con el mismo RUC; solo choca con una botica APROBADO (uq_botica_ruc_aprobado en la BD).
     */
    private void exigirRucLibre(String ruc) {
        if (boticas.existsByRucAndEstado(ruc, APROBADO)) {
            throw ApiException.conflict("El RUC ya está registrado");
        }
    }

    /**
     * A7: un RUC 10 o 15 es de una persona natural; la cuenta debe estar a su nombre. Cada palabra del nombre
     * de la cuenta debe aparecer en el nombre que da SUNAT, sin importar tildes, mayúsculas ni orden.
     * Si SUNAT no respondió (no hay nombre) no se bloquea. Los RUC 17 y 20 no se revisan.
     */
    private void exigirTitular(Long usuarioId, String ruc, RucResponse sunat) {
        if (!esDePersona(ruc) || sunat.razonSocial() == null || sunat.razonSocial().isBlank()) {
            return;
        }
        String nombreCuenta = usuarios.findById(usuarioId).map(u -> u.getNombre()).orElse("");
        if (!nombreCoincide(nombreCuenta, sunat.razonSocial())) {
            // S4-06: no repite el nombre que da SUNAT, para no dictar el nombre que hay que ponerse
            throw ApiException.badRequest("Este RUC pertenece a una persona: la cuenta debe estar a nombre del titular del RUC");
        }
    }

    static boolean esDePersona(String ruc) {
        return ruc.startsWith("10") || ruc.startsWith("15");
    }

    /** A7: true si cada palabra de la cuenta está entre las del titular. Una cuenta sin palabras no coincide. */
    static boolean nombreCoincide(String nombreCuenta, String titular) {
        List<String> palabrasCuenta = palabras(nombreCuenta);
        List<String> palabrasTitular = palabras(titular);
        return !palabrasCuenta.isEmpty() && palabrasTitular.containsAll(palabrasCuenta);
    }

    /** Sin tildes (la Ñ queda como N en los dos lados), en mayúsculas, separadas por lo que no es letra o número. */
    private static List<String> palabras(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return Arrays.stream(sinTildes.split("[^\\p{L}\\p{N}]+")).filter(p -> !p.isEmpty()).toList();
    }

    /** H2: la razón social de SUNAT reemplaza a la enviada; si SUNAT no respondió, se usa la enviada. */
    private static String razonSocial(BoticaRequest r, RucResponse sunat) {
        String razon = sunat.razonSocial() != null ? sunat.razonSocial() : r.razonSocial().trim();
        return razon.length() > 200 ? razon.substring(0, 200) : razon;
    }

    private void aplicar(Botica b, BoticaRequest r, String ruc, String razonSocial) {
        b.setNombreComercial(r.nombreComercial().trim());
        b.setRuc(ruc);
        b.setRazonSocial(razonSocial);
        b.setDireccion(r.direccion().trim());
        b.setDistrito(r.distrito().trim());
        b.setTelefono(r.telefono() == null || r.telefono().isBlank() ? null : r.telefono().trim());
        b.setLatitud(r.latitud());
        b.setLongitud(r.longitud());
    }

    /** H3: latitud y longitud son opcionales, pero van juntas. */
    private void validarUbicacion(BoticaRequest r) {
        if ((r.latitud() == null) != (r.longitud() == null)) {
            throw ApiException.badRequest("Debes enviar latitud y longitud juntas");
        }
    }
}