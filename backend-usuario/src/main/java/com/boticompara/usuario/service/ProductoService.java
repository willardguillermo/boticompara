package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ProductoRequest;
import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final BoticaRepository boticas;
    private final ProductoRepository productos;

    /** H8 */
    @Transactional
    public ProductoResponse crear(Long usuarioId, ProductoRequest r) {
        Botica botica = boticaAprobada(usuarioId);
        Producto p = new Producto();
        p.setBoticaId(botica.getId());
        aplicar(p, r);
        return ProductoResponse.from(productos.save(p));
    }

    /** H9 */
    @Transactional
    public ProductoResponse actualizar(Long usuarioId, Long id, ProductoRequest r) {
        Botica botica = boticaAprobada(usuarioId);
        Producto p = propio(id, botica);
        aplicar(p, r);
        return ProductoResponse.from(productos.save(p));
    }

    /** H10: baja lógica, el registro se conserva con activo = false. */
    @Transactional
    public void eliminar(Long usuarioId, Long id) {
        Botica botica = boticaAprobada(usuarioId);
        Producto p = propio(id, botica);
        p.setActivo(false);
        productos.save(p);
    }

    private Botica boticaAprobada(Long usuarioId) {
        Botica b = boticas.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.forbidden("Primero debes registrar tu botica"));
        if (!"APROBADO".equals(b.getEstado())) {
            throw ApiException.forbidden("Tu botica aún no está aprobada (estado: " + b.getEstado() + ")");
        }
        return b;
    }

    /** 404 si no existe, ya fue dado de baja o es de otra botica. */
    private Producto propio(Long id, Botica botica) {
        return productos.findByIdAndBoticaIdAndActivoTrue(id, botica.getId())
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado"));
    }

    private void aplicar(Producto p, ProductoRequest r) {
        p.setNombreComercial(r.nombreComercial().trim());
        p.setPrincipioActivo(r.principioActivo().trim());
        p.setPresentacion(r.presentacion().trim());
        p.setPrecio(r.precio());
        p.setStock(r.stock());
    }
}
