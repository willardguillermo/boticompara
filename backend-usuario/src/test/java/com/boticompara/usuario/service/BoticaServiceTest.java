package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BoticaDetalleResponse;
import com.boticompara.usuario.dto.BoticaRequest;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoticaServiceTest {

    private static final String RUC_A = "20601234565";
    private static final String RUC_B = "20712345676";
    private static final BigDecimal LAT = new BigDecimal("-11.942500");
    private static final BigDecimal LON = new BigDecimal("-76.699000");

    @Mock
    BoticaRepository boticas;

    @InjectMocks
    BoticaService service;

    private BoticaRequest request(String ruc, BigDecimal lat, BigDecimal lon) {
        return new BoticaRequest("Botica Corregida", ruc, "Razón Corregida S.A.C.",
                "Av. Nueva 123", "Ate", "014567890", lat, lon);
    }

    private Botica botica(String estado, String ruc) {
        Botica b = new Botica();
        b.setId(1L);
        b.setUsuarioId(10L);
        b.setNombreComercial("Botica Vieja");
        b.setRuc(ruc);
        b.setRazonSocial("Razón Vieja");
        b.setDireccion("Av. Vieja 1");
        b.setDistrito("Chosica");
        b.setEstado(estado);
        if ("RECHAZADO".equals(estado)) {
            b.setMotivoRechazo("La dirección no coincide con la ficha RUC");
        }
        return b;
    }

    // ---------- H3: ubicación al registrar ----------

    @Test
    void registrarGuardaLatitudYLongitud() {
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        service.registrar(10L, request(RUC_A, LAT, LON));

        ArgumentCaptor<Botica> captor = ArgumentCaptor.forClass(Botica.class);
        verify(boticas).save(captor.capture());
        assertEquals(LAT, captor.getValue().getLatitud());
        assertEquals(LON, captor.getValue().getLongitud());
        assertEquals("PENDIENTE", captor.getValue().getEstado());
    }

    @Test
    void registrarSinUbicacionEsValido() {
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        service.registrar(10L, request(RUC_A, null, null));

        ArgumentCaptor<Botica> captor = ArgumentCaptor.forClass(Botica.class);
        verify(boticas).save(captor.capture());
        assertNull(captor.getValue().getLatitud());
        assertNull(captor.getValue().getLongitud());
    }

    @Test
    void registrarRechazaCoordenadasIncompletas() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.registrar(10L, request(RUC_A, LAT, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(boticas, never()).save(any(Botica.class));
    }

    // ---------- H7: corregir y reenviar ----------

    @Test
    void corregirDejaLaBoticaPendienteYBorraElMotivo() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaDetalleResponse resp = service.corregir(10L, request(RUC_A, LAT, LON));

        assertEquals("PENDIENTE", resp.estado());
        assertNull(resp.motivoRechazo());
        assertNull(b.getMotivoRechazo());
        assertEquals("Botica Corregida", b.getNombreComercial());
        assertEquals("Av. Nueva 123", b.getDireccion());
        assertEquals(LAT, b.getLatitud());
        // Su propio RUC no cuenta como duplicado: ni se consulta
        verify(boticas, never()).existsByRuc(any());
    }

    @Test
    void corregirPermiteCambiarElRucSiNoLoUsaOtraBotica() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
        when(boticas.existsByRuc(RUC_B)).thenReturn(false);
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaDetalleResponse resp = service.corregir(10L, request(RUC_B, null, null));

        assertEquals(RUC_B, resp.ruc());
        assertEquals("PENDIENTE", resp.estado());
    }

    @Test
    void corregirRechazaSiElRucPerteneceAOtraBotica() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
        when(boticas.existsByRuc(RUC_B)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.corregir(10L, request(RUC_B, null, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void corregirNoEstaPermitidoSiLaBoticaEstaAprobada() {
        Botica b = botica("APROBADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.corregir(10L, request(RUC_A, null, null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("APROBADO", b.getEstado());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void corregirNoEstaPermitidoSiLaBoticaEstaPendiente() {
        Botica b = botica("PENDIENTE", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.corregir(10L, request(RUC_A, null, null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void corregirRechazaRucInvalido() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.corregir(10L, request("20601234566", null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("RECHAZADO", b.getEstado());
    }

    @Test
    void corregirDaNotFoundSiNoTieneBotica() {
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.corregir(10L, request(RUC_A, null, null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}