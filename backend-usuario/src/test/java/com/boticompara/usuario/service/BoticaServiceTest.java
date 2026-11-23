package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BoticaCreadaResponse;
import com.boticompara.usuario.dto.BoticaDetalleResponse;
import com.boticompara.usuario.dto.BoticaRequest;
import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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

    @Mock
    RucSunatService rucSunat;

    @Mock
    UsuarioRepository usuarios;

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

    /** Por defecto SUNAT no responde: se acepta con la validación local y se usa la razón social enviada. */
    @BeforeEach
    void sunatNoDisponible() {
        lenient().when(rucSunat.exigirValido(anyString())).thenAnswer(i -> new RucResponse(i.getArgument(0), true,
                "RUC válido (no se pudo consultar SUNAT)", null, null, null, "LOCAL", null, null));
    }

    private static RucResponse sunatActivo(String ruc) {
        return new RucResponse(ruc, true, "RUC válido", "SALUD TOTAL FARMA S.A.C.", "ACTIVO", "HABIDO", "SUNAT", null, null);
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
        // Su propia botica está RECHAZADO: conservar el RUC no choca consigo misma
        verify(boticas).existsByRucAndEstado(RUC_A, "APROBADO");
    }

    @Test
    void corregirPermiteCambiarElRucSiNoLoUsaOtraBotica() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
        when(boticas.existsByRucAndEstado(RUC_B, "APROBADO")).thenReturn(false);
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaDetalleResponse resp = service.corregir(10L, request(RUC_B, null, null));

        assertEquals(RUC_B, resp.ruc());
        assertEquals("PENDIENTE", resp.estado());
    }

    @Test
    void corregirRechazaSiElRucPerteneceAOtraBoticaAprobada() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
        when(boticas.existsByRucAndEstado(RUC_B, "APROBADO")).thenReturn(true);

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

    // ---------- H2: verificación del RUC en SUNAT ----------

    @Test
    void registrarGuardaLaRazonSocialDeSunat() {
        when(rucSunat.exigirValido(RUC_A)).thenReturn(sunatActivo(RUC_A));
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaCreadaResponse resp = service.registrar(10L, request(RUC_A, null, null));

        assertEquals("SALUD TOTAL FARMA S.A.C.", resp.razonSocial());
    }

    @Test
    void registrarSinRespuestaDeSunatGuardaLaRazonSocialEnviada() {
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaCreadaResponse resp = service.registrar(10L, request(RUC_A, null, null));

        assertEquals("Razón Corregida S.A.C.", resp.razonSocial());
    }

    @Test
    void registrarRechazadoPorSunatNoGuardaLaBotica() {
        when(rucSunat.exigirValido(RUC_A)).thenThrow(ApiException.badRequest("El RUC no existe en SUNAT"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.registrar(10L, request(RUC_A, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("El RUC no existe en SUNAT", ex.getMessage());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void registrarConRucDeUnaBoticaAprobadaNoConsultaSunat() {
        when(boticas.existsByRucAndEstado(RUC_A, "APROBADO")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.registrar(10L, request(RUC_A, null, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(rucSunat, never()).exigirValido(anyString());
    }

    @Test
    void corregirGuardaLaRazonSocialDeSunat() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
        when(rucSunat.exigirValido(RUC_A)).thenReturn(sunatActivo(RUC_A));
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaDetalleResponse resp = service.corregir(10L, request(RUC_A, null, null));

        assertEquals("SALUD TOTAL FARMA S.A.C.", resp.razonSocial());
    }

    // ---------- A5: el RUC solo se reserva al aprobar ----------

    @Test
    void registrarConRucDeOtraSolicitudPendienteSeAcepta() {
        // existsByRucAndEstado(RUC_A, "APROBADO") es false: otra cuenta solo lo tiene PENDIENTE o RECHAZADO
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaCreadaResponse resp = service.registrar(10L, request(RUC_A, null, null));

        assertEquals("PENDIENTE", resp.estado());
        verify(boticas).existsByRucAndEstado(RUC_A, "APROBADO");
    }

    @Test
    void registrarConRucDeUnaBoticaAprobadaDa409() {
        when(boticas.existsByRucAndEstado(RUC_A, "APROBADO")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.registrar(10L, request(RUC_A, null, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("El RUC ya está registrado", ex.getMessage());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void corregirConSuMismoRucYaAcreditadoPorOtraCuentaDa409() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
        when(boticas.existsByRucAndEstado(RUC_A, "APROBADO")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.corregir(10L, request(RUC_A, null, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("RECHAZADO", b.getEstado());
        verify(boticas, never()).save(any(Botica.class));
    }

    // ---------- A7: RUC de persona natural (10 y 15) a nombre de la cuenta ----------

    private static final String RUC_PERSONA = "10461234564";
    private static final String RUC_PERSONA_15 = "15123456782";

    private void cuentaA(String nombre) {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setNombre(nombre);
        when(usuarios.findById(10L)).thenReturn(Optional.of(u));
    }

    private void sunatConTitular(String ruc, String titular) {
        when(rucSunat.exigirValido(ruc)).thenReturn(
                new RucResponse(ruc, true, "RUC válido", titular, "ACTIVO", "HABIDO", "SUNAT", null, null));
    }

    @Test
    void registrarRucDePersonaANombreDeLaCuentaSeAcepta() {
        cuentaA("Rosa Quispe");
        sunatConTitular(RUC_PERSONA, "QUISPE MAMANI ROSA");
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaCreadaResponse resp = service.registrar(10L, request(RUC_PERSONA, null, null));

        assertEquals("PENDIENTE", resp.estado());
        assertEquals("QUISPE MAMANI ROSA", resp.razonSocial());
    }

    @Test
    void registrarRucDePersonaANombreDeOtraPersonaDa400() {
        cuentaA("Jorge Mendoza");
        sunatConTitular(RUC_PERSONA, "QUISPE MAMANI ROSA");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.registrar(10L, request(RUC_PERSONA, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Este RUC pertenece a una persona: la cuenta debe estar a nombre del titular "
                + "(según SUNAT: QUISPE MAMANI ROSA)", ex.getMessage());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void registrarRuc15TambienSeRevisa() {
        cuentaA("Rosa Quispe");
        sunatConTitular(RUC_PERSONA_15, "TORRES DIAZ LUCIA");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.registrar(10L, request(RUC_PERSONA_15, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void registrarRucDePersonaSinRespuestaDeSunatNoSeBloquea() {
        // Por defecto SUNAT no responde (fuente LOCAL, sin nombre del titular)
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        BoticaCreadaResponse resp = service.registrar(10L, request(RUC_PERSONA, null, null));

        assertEquals("PENDIENTE", resp.estado());
        verify(usuarios, never()).findById(any());
    }

    @Test
    void registrarRucDeEmpresaNoRevisaElTitular() {
        sunatConTitular(RUC_A, "SALUD TOTAL FARMA S.A.C.");
        when(boticas.save(any(Botica.class))).thenAnswer(i -> i.getArgument(0));

        service.registrar(10L, request(RUC_A, null, null));

        verify(usuarios, never()).findById(any());
    }

    @Test
    void corregirTambienExigeQueElRucDePersonaSeaDeLaCuenta() {
        Botica b = botica("RECHAZADO", RUC_A);
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(b));
        cuentaA("Jorge Mendoza");
        sunatConTitular(RUC_PERSONA, "QUISPE MAMANI ROSA");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.corregir(10L, request(RUC_PERSONA, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("RECHAZADO", b.getEstado());
        verify(boticas, never()).save(any(Botica.class));
    }

    @Test
    void nombreCoincideSinTildesMayusculasNiOrden() {
        assertTrue(BoticaService.nombreCoincide("Rosa Quispe", "QUISPE MAMANI ROSA"));
        assertTrue(BoticaService.nombreCoincide("josé  peña", "PEÑA RAMIREZ JOSE"));
        assertTrue(BoticaService.nombreCoincide("Lucía Torres", "TORRES, DIAZ LUCIA"));
        // Una palabra de la cuenta que no está en SUNAT basta para que no coincida
        assertFalse(BoticaService.nombreCoincide("Rosa Quispe Mendoza", "QUISPE MAMANI ROSA"));
        // No basta con que sea el inicio de una palabra
        assertFalse(BoticaService.nombreCoincide("Ros Quispe", "QUISPE MAMANI ROSA"));
        assertFalse(BoticaService.nombreCoincide("   ", "QUISPE MAMANI ROSA"));
    }
}
