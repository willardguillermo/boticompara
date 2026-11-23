package com.boticompara.usuario.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** H85-H87: formato de reseñas (API.md, sección 11). */
public final class ResenaDtos {

    private ResenaDtos() {
    }

    public record Guardar(@NotNull(message = "Las estrellas son obligatorias") Integer estrellas,
                          String comentario) {
    }

    public record Propia(Long id, Long boticaId, Short estrellas, String comentario, String respuesta,
                         Instant respondidaEn, boolean oculta, Instant creadoEn, Instant actualizadoEn) {
    }

    public record Item(Long id, String autor, Short estrellas, String comentario, String respuesta,
                       Instant respondidaEn, Instant creadoEn, Instant actualizadoEn) {
    }

    public record Listado(Long boticaId, BigDecimal promedio, long total, int page, int totalPages,
                          List<Item> resenas, Propia miResena) {
    }
}