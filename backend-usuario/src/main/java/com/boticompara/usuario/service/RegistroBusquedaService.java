package com.boticompara.usuario.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.Locale;

/**
 * H62: registra cada búsqueda del comprador en la tabla busqueda, para los reportes del administrador.
 * Se llama desde BusquedaController después de BusquedaService.buscar(): así queda fuera de su transacción
 * de solo lectura, donde PostgreSQL rechazaría el insert. No guarda la ubicación del comprador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroBusquedaService {

    /** Largo de la columna busqueda.texto. */
    static final int MAX_TEXTO = 150;

    private final NamedParameterJdbcTemplate jdbc;

    /** Si el insert falla, solo queda un aviso en el log: la búsqueda responde igual. */
    public void registrar(String q, String tipo, int resultados, Long usuarioId) {
        String texto = q.trim().toLowerCase(Locale.ROOT);
        if (texto.length() > MAX_TEXTO) {
            texto = texto.substring(0, MAX_TEXTO);
        }
        try {
            jdbc.update("insert into busqueda (usuario_id, texto, tipo, resultados) "
                            + "values (:usuario, :texto, :tipo, :resultados)",
                    new MapSqlParameterSource()
                            .addValue("usuario", usuarioId, Types.BIGINT)
                            .addValue("texto", texto)
                            .addValue("tipo", tipo)
                            .addValue("resultados", resultados));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la búsqueda: {}", e.toString());
        }
    }
}