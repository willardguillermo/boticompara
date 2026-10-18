package com.boticompara.usuario.dto;

/** H31: medicamento del catálogo maestro para el selector de la web. */
public record MedicamentoResponse(Long id, String nombreComercial, String principioActivo,
                                  String presentacion, String concentracion) {
}
