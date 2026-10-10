package com.boticompara.usuario.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipoArchivoTest {

    @Test
    void detectaPdf() {
        Optional<TipoArchivo> t = TipoArchivo.detectar("%PDF-1.7 contenido".getBytes(StandardCharsets.US_ASCII));
        assertTrue(t.isPresent());
        assertEquals("pdf", t.get().extension());
        assertEquals("application/pdf", t.get().contentType());
    }

    @Test
    void detectaJpg() {
        Optional<TipoArchivo> t = TipoArchivo.detectar(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00});
        assertTrue(t.isPresent());
        assertEquals("jpg", t.get().extension());
        assertEquals("image/jpeg", t.get().contentType());
    }

    @Test
    void detectaPng() {
        Optional<TipoArchivo> t = TipoArchivo.detectar(
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00});
        assertTrue(t.isPresent());
        assertEquals("png", t.get().extension());
        assertEquals("image/png", t.get().contentType());
    }

    @Test
    void rechazaTextoYEjecutables() {
        assertTrue(TipoArchivo.detectar("hola".getBytes(StandardCharsets.US_ASCII)).isEmpty());
        assertTrue(TipoArchivo.detectar(new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00}).isEmpty());
    }

    @Test
    void rechazaNuloVacioOMuyCorto() {
        assertTrue(TipoArchivo.detectar(null).isEmpty());
        assertTrue(TipoArchivo.detectar(new byte[0]).isEmpty());
        assertTrue(TipoArchivo.detectar(new byte[]{0x25, 0x50}).isEmpty());
    }
}