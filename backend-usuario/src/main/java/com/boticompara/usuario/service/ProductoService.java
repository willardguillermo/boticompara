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
    private final MedicamentoService medicamentos;

    /** H8 */
    @Transactional
    public ProductoResponse crear(Long usuarioId, ProductoRequest r) {
        Botica botica = boticaAprobada(usuarioId);
        String principioDelMedicamento = principioDelMedicamento(r);
        Producto p = new Producto();
        p.setBoticaId(botica.getId());
        aplicar(p, r, principioDelMedicamento);
        return ProductoResponse.from(productos.save(p));
    }

    /** H9 */
    @Transactional
    public ProductoResponse actualizar(Long usuarioId, Long id, ProductoRequest r) {
        Botica botica = boticaAprobada(usuarioId);
        Producto p = propio(id, botica);
        String principioDelMedicamento = r.medicamentoId() != null
                ? principioDelMedicamento(r)
                : principioDelEnlaceActual(p);
        aplicar(p, r, principioDelMedicamento);
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

    private void aplicar(Producto p, ProductoRequest r, String principioDelMedicamento) {
        p.setNombreComercial(r.nombreComercial().trim());
        // D2: si el producto queda enlazado se guarda el principio activo del medicamento; si no, el que envía la web
        p.setPrincipioActivo(principioDelMedicamento != null ? principioDelMedicamento : r.principioActivo().trim());
        p.setPresentacion(r.presentacion().trim());
        p.setPrecio(r.precio());
        p.setStock(r.stock());
        // H12: si no viene, al crear queda el valor por defecto (5) y al editar se conserva el actual
        if (r.stockMinimo() != null) {
            p.setStockMinimo(r.stockMinimo());
        }
        // H31: igual que stockMinimo, si no viene se conserva el enlace actual
        if (r.medicamentoId() != null) {
            p.setMedicamentoId(r.medicamentoId());
        }
    }

    /**
     * H31: 400 si el medicamento enviado no existe o está oculto.
     * D2: devuelve su principio activo; null si no se envía medicamentoId.
     */
    private String principioDelMedicamento(ProductoRequest r) {
        return r.medicamentoId() == null ? null : medicamentos.principioActivoEnlazable(r.medicamentoId());
    }


    /** D2: sin medicamentoId se conserva el enlace, y con él el principio activo del medicamento enlazado. */
    private String principioDelEnlaceActual(Producto p) {
        return p.getMedicamentoId() == null ? null : medicamentos.principioActivoDe(p.getMedicamentoId());
    }
    /**
     * H31: quita el enlace del producto con el catálogo maestro. Es idempotente: si no estaba enlazado,
     * no pasa nada. El producto sigue activo y conserva todos sus datos.
     */
    @Transactional
    public void desenlazarMedicamento(Long usuarioId, Long id) {
        Botica botica = boticaAprobada(usuarioId);
        Producto p = propio(id, botica);
        if (p.getMedicamentoId() != null) {
            p.setMedicamentoId(null);
            productos.save(p);
        }
    }
}