package com.boticompara.usuario.dto;

import java.util.List;

/** H36 y H41: alternativas de un producto; sinEquivalentes es true cuando la lista viene vacía. */
public record AlternativasResponse(Long productoId, boolean sinEquivalentes, List<AlternativaResponse> alternativas) {
}
