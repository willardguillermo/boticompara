package com.boticompara.usuario.repository;

import com.boticompara.usuario.BaseDePrueba;
import com.boticompara.usuario.entity.EstadoReserva;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S4-02: las dos consultas con bloqueo de la baja del dueño corren en PostgreSQL (select ... for update). Usan ids que
 * no existen, así que no bloquean ni cambian nada, y @DataJpaTest deshace la transacción al terminar. Sin base
 * configurada, la clase se omite.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class BajaDuenoConsultasTest {

    /** Un id que ninguna fila tiene. */
    private static final long NADIE = -1L;

    @Autowired BoticaRepository boticas;
    @Autowired ReservaRepository reservas;

    BajaDuenoConsultasTest() {
        BaseDePrueba.exigir();
    }

    @Test
    void bloquearLaBoticaDeUnUsuarioCorreEnLaBase() {
        assertTrue(boticas.bloquearPorUsuarioId(NADIE).isEmpty());
    }

    @Test
    void bloquearLasReservasActivasDeUnaBoticaCorreEnLaBase() {
        assertTrue(reservas.bloquearDeBoticaConEstados(NADIE, EstadoReserva.ACTIVAS).isEmpty());
    }
}
