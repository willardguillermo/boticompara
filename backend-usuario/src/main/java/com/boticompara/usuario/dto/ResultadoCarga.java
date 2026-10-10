package com.boticompara.usuario.dto;

import java.util.List;

/** Reporte de la carga masiva (H11). */
public record ResultadoCarga(int creados, int actualizados, List<ErrorFila> errores) {
}