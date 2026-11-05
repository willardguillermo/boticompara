package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ResultadoCarga;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.util.CsvProductoParser;
import com.boticompara.usuario.util.CsvProductoParser.FilaProducto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CargaMasivaService {

    private final BoticaRepository boticas;
    private final ProductoRepository productos;
    private final AlertaService alertas;

    /**
     * H11: crea los productos nuevos y actualiza precio y stock de los que ya existen (mismo nombre y
     * presentación). Las filas válidas se guardan aunque otras tengan errores; los errores van en el reporte.
     */
    @Transactional
    public ResultadoCarga cargar(Long usuarioId, byte[] datos) {
        Botica botica = boticaAprobada(usuarioId);
        CsvProductoParser.Resultado csv = CsvProductoParser.parsearArchivo(datos);

        Map<String, Producto> existentes = new HashMap<>();
        for (Producto p : productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(botica.getId())) {
            existentes.putIfAbsent(FilaProducto.clave(p.getNombreComercial(), p.getPresentacion()), p);
        }

        List<Producto> aGuardar = new ArrayList<>();
        // H59: precio de cada producto existente antes de la carga (el primero, si aparece en varias filas)
        Map<Producto, BigDecimal> preciosAntes = new LinkedHashMap<>();
        int creados = 0;
        int actualizados = 0;
        for (FilaProducto f : csv.filas()) {
            Producto p = existentes.get(f.clave());
            if (p != null) {
                preciosAntes.putIfAbsent(p, p.getPrecio());
                p.setPrecio(f.precio());
                p.setStock(f.stock());
                if (f.stockMinimo() != null) {
                    p.setStockMinimo(f.stockMinimo());
                }
                actualizados++;
            } else {
                p = new Producto();
                p.setBoticaId(botica.getId());
                p.setNombreComercial(f.nombreComercial());
                p.setPrincipioActivo(f.principioActivo());
                p.setPresentacion(f.presentacion());
                p.setPrecio(f.precio());
                p.setStock(f.stock());
                if (f.stockMinimo() != null) {
                    p.setStockMinimo(f.stockMinimo());
                }
                creados++;
            }
            aGuardar.add(p);
        }
        if (!aGuardar.isEmpty()) {
            productos.saveAll(aGuardar);
        }
        registrarBajas(preciosAntes);
        return new ResultadoCarga(creados, actualizados, csv.errores());
    }

    /** H59: alertas de los productos que bajaron de precio, en la misma transacción que la carga. */
    private void registrarBajas(Map<Producto, BigDecimal> preciosAntes) {
        List<Map.Entry<Producto, BigDecimal>> bajas = preciosAntes.entrySet().stream()
                .filter(e -> e.getKey().getPrecio().compareTo(e.getValue()) < 0)
                .toList();
        if (bajas.isEmpty()) {
            return;
        }
        // Deja los cambios en la BD para que la vista de búsqueda (JDBC) vea los productos ya actualizados
        productos.flush();
        for (Map.Entry<Producto, BigDecimal> e : bajas) {
            alertas.registrarBaja(e.getKey().getId(), e.getValue(), e.getKey().getPrecio());
        }
    }

    private Botica boticaAprobada(Long usuarioId) {
        Botica b = boticas.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.forbidden("Primero debes registrar tu botica"));
        if (!"APROBADO".equals(b.getEstado())) {
            throw ApiException.forbidden("Tu botica aún no está aprobada (estado: " + b.getEstado() + ")");
        }
        return b;
    }
}