package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.LicenciaResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenciaServiceTest {

    private static final byte[] PDF = "%PDF-1.7 prueba".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    @Mock
    BoticaService boticaService;

    @Mock
    BoticaRepository boticas;

    @Mock
    StorageService storage;

    @InjectMocks
    LicenciaService service;

    private Botica botica(String licenciaUrl) {
        Botica b = new Botica();
        b.setId(1L);
        b.setUsuarioId(10L);
        b.setLicenciaUrl(licenciaUrl);
        return b;
    }

    @Test
    void subirPdfGuardaLaRutaYMarcaTieneLicencia() {
        when(boticaService.obtenerMia(10L)).thenReturn(botica(null));

        LicenciaResponse resp = service.subir(10L, PDF);

        verify(storage).subir("botica-1/licencia.pdf", PDF, "application/pdf");
        ArgumentCaptor<Botica> captor = ArgumentCaptor.forClass(Botica.class);
        verify(boticas).save(captor.capture());
        assertEquals("botica-1/licencia.pdf", captor.getValue().getLicenciaUrl());
        assertTrue(resp.tieneLicencia());
        verify(storage, never()).eliminar(any());
    }

    @Test
    void reemplazarConOtroFormatoBorraElArchivoAnterior() {
        when(boticaService.obtenerMia(10L)).thenReturn(botica("botica-1/licencia.pdf"));

        service.subir(10L, PNG);

        verify(storage).subir("botica-1/licencia.png", PNG, "image/png");
        verify(storage).eliminar("botica-1/licencia.pdf");
        ArgumentCaptor<Botica> captor = ArgumentCaptor.forClass(Botica.class);
        verify(boticas).save(captor.capture());
        assertEquals("botica-1/licencia.png", captor.getValue().getLicenciaUrl());
    }

    @Test
    void reemplazarConElMismoFormatoNoBorraNada() {
        when(boticaService.obtenerMia(10L)).thenReturn(botica("botica-1/licencia.png"));

        service.subir(10L, PNG);

        verify(storage).subir("botica-1/licencia.png", PNG, "image/png");
        verify(storage, never()).eliminar(any());
    }

    @Test
    void rechazaFormatoNoPermitido() {
        when(boticaService.obtenerMia(10L)).thenReturn(botica(null));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.subir(10L, "hola".getBytes(StandardCharsets.US_ASCII)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(storage, never()).subir(any(), any(), any());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void rechazaArchivoVacio() {
        when(boticaService.obtenerMia(10L)).thenReturn(botica(null));

        ApiException ex = assertThrows(ApiException.class, () -> service.subir(10L, new byte[0]));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(storage, never()).subir(any(), any(), any());
    }

    @Test
    void rechazaArchivoDeMasDeCincoMb() {
        when(boticaService.obtenerMia(10L)).thenReturn(botica(null));
        byte[] grande = new byte[LicenciaService.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);

        ApiException ex = assertThrows(ApiException.class, () -> service.subir(10L, grande));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(storage, never()).subir(any(), any(), any());
    }

    @Test
    void noGuardaLaRutaSiFallaLaSubida() {
        when(boticaService.obtenerMia(10L)).thenReturn(botica(null));
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "fallo")).when(storage).subir(any(), any(), any());

        ApiException ex = assertThrows(ApiException.class, () -> service.subir(10L, PDF));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void devuelve404SiNoTieneBotica() {
        when(boticaService.obtenerMia(10L)).thenThrow(ApiException.notFound("Aún no registraste tu botica"));

        ApiException ex = assertThrows(ApiException.class, () -> service.subir(10L, PDF));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(storage, never()).subir(any(), any(), any());
    }
}