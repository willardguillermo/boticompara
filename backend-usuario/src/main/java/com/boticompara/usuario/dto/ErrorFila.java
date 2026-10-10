package com.boticompara.usuario.dto;

/** Error de una fila del CSV de carga masiva (H11). fila = número de línea del archivo; el encabezado es la fila 1. */
public record ErrorFila(int fila, String mensaje) {
}