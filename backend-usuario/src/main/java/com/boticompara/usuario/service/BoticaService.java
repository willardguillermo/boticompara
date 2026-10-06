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

    /** H1, H2: la botica queda PENDIENTE hasta que el admin la apruebe en Django. */
    @Transactional
    public BoticaCreadaResponse registrar(Long usuarioId, BoticaRequest r) {
        String ruc = r.ruc().trim();
        if (!RucValidator.esValido(ruc)) {
            throw ApiException.badRequest("El RUC no es válido");
        }
        if (boticas.existsByUsuarioId(usuarioId)) {
            throw ApiException.conflict("Ya tienes una botica registrada");
        }
        if (boticas.existsByRuc(ruc)) {
            throw ApiException.conflict("El RUC ya está registrado");
        }
        Botica b = new Botica();
        b.setUsuarioId(usuarioId);
        b.setNombreComercial(r.nombreComercial().trim());
        b.setRuc(ruc);
        b.setRazonSocial(r.razonSocial().trim());
        b.setDireccion(r.direccion().trim());
        b.setDistrito(r.distrito().trim());
        b.setTelefono(r.telefono() == null || r.telefono().isBlank() ? null : r.telefono().trim());
        b.setEstado("PENDIENTE");
        return BoticaCreadaResponse.from(boticas.save(b));
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
}
