package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.limite.LimiteExcedidoException;
import com.boticompara.usuario.repository.CodigoCorreoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * H1, H20: códigos de 6 dígitos y tokenVerificacion (docs/API.md, sección 1). El repositorio es una lista en memoria
 * que imita las consultas de CodigoCorreoRepository, y el reloj se mueve a mano.
 */
class CodigoCorreoServiceTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres";
    private static final String CORREO = "ana@correo.pe";
    private static final Proposito REG = Proposito.REGISTRO;

    static final class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-11-26T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    final List<CodigoCorreo> filas = new ArrayList<>();
    CodigoCorreoRepository repo;
    RelojMovil reloj;
    CodigoCorreoService service;
    private long siguienteId = 1;

    @BeforeEach
    void preparar() {
        repo = mock(CodigoCorreoRepository.class);
        lenient().when(repo.save(any(CodigoCorreo.class))).thenAnswer(i -> {
            CodigoCorreo c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(siguienteId++);
                filas.add(c);
            }
            return c;
        });
        lenient().when(repo.findFirstByCorreoAndPropositoOrderByIdDesc(anyString(), any())).thenAnswer(i ->
                delCorreo(i.getArgument(0), i.getArgument(1)).max(Comparator.comparing(CodigoCorreo::getId)));
        lenient().when(repo.findByCorreoAndPropositoAndCreadoEnAfterOrderByIdAsc(anyString(), any(), any()))
                .thenAnswer(i -> delCorreo(i.getArgument(0), i.getArgument(1))
                        .filter(c -> c.getCreadoEn().isAfter(i.getArgument(2)))
                        .sorted(Comparator.comparing(CodigoCorreo::getId)).toList());
        lenient().when(repo.anularPendientes(anyString(), any())).thenAnswer(i -> {
            List<CodigoCorreo> pendientes = delCorreo(i.getArgument(0), i.getArgument(1))
                    .filter(c -> !c.isAnulado() && c.getUsadoEn() == null).toList();
            pendientes.forEach(c -> c.setAnulado(true));
            return pendientes.size();
        });
        lenient().when(repo.findByTokenHash(anyString())).thenAnswer(i -> porToken(i.getArgument(0)));
        lenient().when(repo.bloquearPorTokenHash(anyString())).thenAnswer(i -> porToken(i.getArgument(0)));
        reloj = new RelojMovil();
        service = new CodigoCorreoService(repo, SECRETO, reloj);
    }

    private java.util.stream.Stream<CodigoCorreo> delCorreo(String correo, Proposito p) {
        return filas.stream().filter(c -> c.getCorreo().equals(correo) && c.getProposito() == p);
    }

    private Optional<CodigoCorreo> porToken(String hash) {
        return filas.stream().filter(c -> hash.equals(c.getTokenHash())).findFirst();
    }

    private void codigoInvalido(String codigo) {
        ApiException e = assertThrows(ApiException.class, () -> service.confirmar(CORREO, REG, codigo));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("El código no es válido o venció. Pide uno nuevo", e.getMessage());
    }

    private static String otro(String codigo) {
        return codigo.equals("000000") ? "111111" : "000000";
    }

    // ---------- emitir ----------

    @Test
    void emitirGuardaSoloElHashDeUnCodigoDeSeisDigitosQueVenceEn15Minutos() {
        String codigo = service.emitir(CORREO, REG);

        assertTrue(codigo.matches("[0-9]{6}"));
        CodigoCorreo c = filas.get(0);
        assertEquals(CORREO, c.getCorreo());
        assertEquals(REG, c.getProposito());
        assertEquals(64, c.getCodigoHash().length());
        assertFalse(c.getCodigoHash().contains(codigo));
        assertEquals(service.hashCodigo(CORREO, REG, codigo), c.getCodigoHash());
        assertEquals(reloj.ahora.plus(Duration.ofMinutes(15)), c.getVenceEn());
        assertEquals(reloj.ahora, c.getCreadoEn());
        assertEquals(0, c.getIntentos());
        assertNull(c.getTokenHash());
        verify(repo).bloquearCorreo("REGISTRO:" + CORREO);
    }

    @Test
    void elHashDependeDelCorreoYDelProposito() {
        assertNotEquals(service.hashCodigo(CORREO, REG, "123456"), service.hashCodigo("otro@correo.pe", REG, "123456"));
        assertNotEquals(service.hashCodigo(CORREO, REG, "123456"),
                service.hashCodigo(CORREO, Proposito.VERIFICACION, "123456"));
    }

    @Test
    void otroCodigoAntesDe60SegundosDa429ConLosSegundosQueFaltan() {
        service.emitir(CORREO, REG);
        reloj.avanzar(Duration.ofSeconds(45));

        LimiteExcedidoException e = assertThrows(LimiteExcedidoException.class, () -> service.emitir(CORREO, REG));

        assertEquals("Espera un momento antes de pedir otro código", e.getMessage());
        assertEquals(15, e.getReintentarEnSegundos());
        assertEquals(1, filas.size());
    }

    @Test
    void pasados60SegundosSeEmiteOtroYAnulaElAnterior() {
        String primero = service.emitir(CORREO, REG);
        reloj.avanzar(Duration.ofSeconds(60));

        String segundo = service.emitir(CORREO, REG);

        assertEquals(2, filas.size());
        assertTrue(filas.get(0).isAnulado());
        assertFalse(filas.get(1).isAnulado());
        // El primer código ya no sirve (salvo que coincida por azar con el nuevo)
        if (!primero.equals(segundo)) {
            codigoInvalido(primero);
        }
        assertDoesNotThrow(() -> service.confirmar(CORREO, REG, segundo));
    }

    @Test
    void alSextoCodigoEnUnaHoraDa429HastaQueSaleElMasAntiguoDeLaVentana() {
        for (int i = 0; i < 5; i++) {
            service.emitir(CORREO, REG);
            reloj.avanzar(Duration.ofMinutes(2));
        }
        // Van 10 minutos desde el primero: faltan 50 para que salga de la ventana
        LimiteExcedidoException e = assertThrows(LimiteExcedidoException.class, () -> service.emitir(CORREO, REG));
        assertEquals("Pediste demasiados códigos para este correo. Intenta más tarde", e.getMessage());
        assertEquals(50 * 60, e.getReintentarEnSegundos());

        reloj.avanzar(Duration.ofMinutes(50).plusSeconds(1));
        assertDoesNotThrow(() -> service.emitir(CORREO, REG));
    }

    @Test
    void losLimitesSonPorCorreoYPorProposito() {
        service.emitir(CORREO, REG);

        assertDoesNotThrow(() -> service.emitir("otro@correo.pe", REG));
        assertDoesNotThrow(() -> service.emitir(CORREO, Proposito.VERIFICACION));
    }

    // ---------- confirmar ----------

    @Test
    void elCodigoCorrectoQuedaVerificadoYNoSirveDosVeces() {
        String codigo = service.emitir(CORREO, REG);

        CodigoCorreo c = service.confirmar(CORREO, REG, " " + codigo + " ");

        assertEquals(reloj.ahora, c.getVerificadoEn());
        codigoInvalido(codigo);
    }

    @Test
    void unCodigoIncorrectoSumaUnIntentoYElQuintoLoInvalidaAunqueDespuesLlegueElCorrecto() {
        String codigo = service.emitir(CORREO, REG);
        for (int i = 1; i <= 5; i++) {
            codigoInvalido(otro(codigo));
            assertEquals(i, filas.get(0).getIntentos());
        }

        codigoInvalido(codigo);
        assertEquals(5, filas.get(0).getIntentos());
        assertNull(filas.get(0).getVerificadoEn());
    }

    @Test
    void conCuatroIntentosFallidosElCorrectoTodaviaSirve() {
        String codigo = service.emitir(CORREO, REG);
        for (int i = 0; i < 4; i++) {
            codigoInvalido(otro(codigo));
        }

        assertNotNull(service.confirmar(CORREO, REG, codigo).getVerificadoEn());
    }

    @Test
    void elCodigoVenceALos15Minutos() {
        String codigo = service.emitir(CORREO, REG);
        reloj.avanzar(Duration.ofMinutes(15));

        codigoInvalido(codigo);
        assertEquals(0, filas.get(0).getIntentos());
    }

    @Test
    void sinCodigoPedidoOConOtroCorreoOProposito_400ConElMismoMensaje() {
        codigoInvalido("123456");
        String codigo = service.emitir(CORREO, REG);

        assertThrows(ApiException.class, () -> service.confirmar("otro@correo.pe", REG, codigo));
        assertThrows(ApiException.class, () -> service.confirmar(CORREO, Proposito.VERIFICACION, codigo));
        assertEquals(0, filas.get(0).getIntentos());
    }

    // ---------- token ----------

    @Test
    void elTokenTiene64HexadecimalesSoloSeGuardaSuHashYVenceEn30Minutos() {
        CodigoCorreo c = service.confirmar(CORREO, REG, service.emitir(CORREO, REG));

        String token = service.emitirToken(c);

        assertTrue(token.matches("[0-9a-f]{64}"));
        assertEquals(service.hashToken(token), c.getTokenHash());
        assertNotEquals(token, c.getTokenHash());
        assertEquals(reloj.ahora.plus(Duration.ofMinutes(30)), c.getTokenVenceEn());
        assertSame(c, service.tokenVigente(token, false));
        assertSame(c, service.tokenVigente(" " + token + " ", true));
    }

    private void tokenInvalido(String token) {
        ApiException e = assertThrows(ApiException.class, () -> service.tokenVigente(token, true));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("Tu verificación del correo venció. Vuelve a pedir un código", e.getMessage());
    }

    @Test
    void elTokenEsDeUnSoloUso() {
        CodigoCorreo c = service.confirmar(CORREO, REG, service.emitir(CORREO, REG));
        String token = service.emitirToken(c);

        service.gastar(c);

        assertEquals(reloj.ahora, c.getUsadoEn());
        tokenInvalido(token);
    }

    @Test
    void elTokenVenceALos30Minutos() {
        String token = service.emitirToken(service.confirmar(CORREO, REG, service.emitir(CORREO, REG)));
        reloj.avanzar(Duration.ofMinutes(29));
        assertDoesNotThrow(() -> service.tokenVigente(token, false));

        reloj.avanzar(Duration.ofMinutes(1));
        tokenInvalido(token);
    }

    @Test
    void pedirOtroCodigoAnulaElTokenAnterior() {
        String token = service.emitirToken(service.confirmar(CORREO, REG, service.emitir(CORREO, REG)));
        reloj.avanzar(Duration.ofMinutes(2));

        service.emitir(CORREO, REG);

        tokenInvalido(token);
    }

    @Test
    void tokenInexistenteVacioODeOtroPropositoNoSirve() {
        tokenInvalido("no-existe");
        tokenInvalido("   ");
        tokenInvalido(null);
        CodigoCorreo c = service.confirmar(CORREO, Proposito.VERIFICACION, service.emitir(CORREO, Proposito.VERIFICACION));
        c.setTokenHash(service.hashToken("token-raro"));
        c.setTokenVenceEn(reloj.ahora.plus(Duration.ofMinutes(30)));
        tokenInvalido("token-raro");
    }

    // ---------- limpieza ----------

    @Test
    void limpiarBorraLasFilasDeMasDeUnDia() {
        service.limpiar();

        verify(repo).borrarCreadosAntesDe(reloj.ahora.minus(Duration.ofDays(1)));
    }

    @Test
    void normalizaElCorreo() {
        assertEquals("ana@correo.pe", CodigoCorreoService.normalizar("  Ana@Correo.PE "));
        assertEquals("", CodigoCorreoService.normalizar(null));
    }
}
