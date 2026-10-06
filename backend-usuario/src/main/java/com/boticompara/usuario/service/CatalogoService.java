package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final BoticaService boticas;
    private final ProductoRepository productos;

    /** H13, H14: catálogo activo propio, con filtro opcional por nombre o principio activo. */
    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(Long usuarioId, String q) {
        Botica botica = boticas.obtenerMia(usuarioId);
        List<Producto> lista;
        if (q == null || q.isBlank()) {
            lista = productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(botica.getId());
        } else {
            String patron = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            lista = productos.buscarEnBotica(botica.getId(), patron);
        }
        return lista.stream().map(ProductoResponse::from).toList();
    }
}
