package com.boticompara.usuario.util;

import java.util.Optional;

/** Tipo de archivo permitido para la licencia, detectado por su contenido (H4). */
public record TipoArchivo(String extension, String contentType) {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};                       // %PDF-
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public static Optional<TipoArchivo> detectar(byte[] datos) {
        if (empieza(datos, PDF)) {
            return Optional.of(new TipoArchivo("pdf", "application/pdf"));
        }
        if (empieza(datos, JPG)) {
            return Optional.of(new TipoArchivo("jpg", "image/jpeg"));
        }
        if (empieza(datos, PNG)) {
            return Optional.of(new TipoArchivo("png", "image/png"));
        }
        return Optional.empty();
    }

    private static boolean empieza(byte[] datos, byte[] firma) {
        if (datos == null || datos.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (datos[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}