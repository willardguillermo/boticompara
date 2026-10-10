package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BoticaCreadaResponse;
import com.boticompara.usuario.dto.BoticaDetalleResponse;
import com.boticompara.usuario.dto.BoticaRequest;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.util.RucValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoticaService {

    private final BoticaRepository boticas;

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
        if (boticas.existsByRuc(ruc)) {
            throw ApiException.conflict("El RUC ya está registrado");
        }
        Botica b = new Botica();
        b.setUsuarioId(usuarioId);
        aplicar(b, r, ruc);
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
        // Conservar su propio RUC no es un duplicado; solo importa si cambió
        if (!ruc.equals(b.getRuc()) && boticas.existsByRuc(ruc)) {
            throw ApiException.conflict("El RUC ya está registrado");
        }
        aplicar(b, r, ruc);
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

    private void aplicar(Botica b, BoticaRequest r, String ruc) {
        b.setNombreComercial(r.nombreComercial().trim());
        b.setRuc(ruc);
        b.setRazonSocial(r.razonSocial().trim());
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