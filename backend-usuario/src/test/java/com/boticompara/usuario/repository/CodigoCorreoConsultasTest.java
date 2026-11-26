package com.boticompara.usuario.repository;

import com.boticompara.usuario.BaseDePrueba;
import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H20: las consultas de codigo_correo corren en PostgreSQL (bloqueo por correo, select ... for update, anular y
 * limpiar) y la entidad coincide con la tabla de v9. @DataJpaTest deshace todo al terminar: no queda nada guardado.
 * Usa un correo que no existe (.invalid). Sin base configurada, la clase se omite.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class CodigoCorreoConsultasTest {

    private static final String CORREO = "prueba-codigo@boticompara.invalid";

    @Autowired CodigoCorreoRepository codigos;
    @Autowired TestEntityManager em;

    CodigoCorreoConsultasTest() {
        BaseDePrueba.exigir();
    }

    private CodigoCorreo nuevo(Instant creado) {
        CodigoCorreo c = new CodigoCorreo();
        c.setCorreo(CORREO);
        c.setProposito(Proposito.REGISTRO);
        c.setCodigoHash("a".repeat(64));
        c.setVenceEn(creado.plus(Duration.ofMinutes(15)));
        c.setCreadoEn(creado);
        return codigos.saveAndFlush(c);
    }

    @Test
    void elBloqueoPorCorreoCorreEnLaBase() {
        assertEquals(1, codigos.bloquearCorreo("REGISTRO:" + CORREO));
    }

    @Test
    void ultimoCodigoConteoPorHoraYAnularPendientes() {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        CodigoCorreo viejo = nuevo(ahora.minus(Duration.ofHours(2)));
        CodigoCorreo reciente = nuevo(ahora.minusSeconds(30));

        assertEquals(reciente.getId(),
                codigos.findFirstByCorreoAndPropositoOrderByIdDesc(CORREO, Proposito.REGISTRO).orElseThrow().getId());
        List<CodigoCorreo> enLaHora = codigos.findByCorreoAndPropositoAndCreadoEnAfterOrderByIdAsc(
                CORREO, Proposito.REGISTRO, ahora.minus(Duration.ofHours(1)));
        assertEquals(List.of(reciente.getId()), enLaHora.stream().map(CodigoCorreo::getId).toList());

        assertEquals(2, codigos.anularPendientes(CORREO, Proposito.REGISTRO));
        em.clear();
        assertTrue(codigos.findById(viejo.getId()).orElseThrow().isAnulado());
    }

    @Test
    void elTokenSeBuscaYSeBloqueaPorSuHash() {
        CodigoCorreo c = nuevo(Instant.now());
        c.setVerificadoEn(Instant.now());
        c.setTokenHash("f".repeat(64));
        c.setTokenVenceEn(Instant.now().plus(Duration.ofMinutes(30)));
        codigos.saveAndFlush(c);
        em.clear();

        assertEquals(c.getId(), codigos.findByTokenHash("f".repeat(64)).orElseThrow().getId());
        assertEquals(c.getId(), codigos.bloquearPorTokenHash("f".repeat(64)).orElseThrow().getId());
        assertFalse(codigos.findByTokenHash("e".repeat(64)).isPresent());
    }

    @Test
    void limpiarBorraSoloLoDeMasDeUnDia() {
        Instant ahora = Instant.now();
        CodigoCorreo viejo = nuevo(ahora.minus(Duration.ofDays(2)));
        CodigoCorreo hoy = nuevo(ahora.minus(Duration.ofHours(3)));

        codigos.borrarCreadosAntesDe(ahora.minus(Duration.ofDays(1)));
        em.clear();

        assertFalse(codigos.findById(viejo.getId()).isPresent());
        assertTrue(codigos.findById(hoy.getId()).isPresent());
    }
}
