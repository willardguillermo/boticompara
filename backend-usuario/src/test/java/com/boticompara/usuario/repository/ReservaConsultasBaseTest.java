package com.boticompara.usuario.repository;

import com.boticompara.usuario.BaseDePrueba;
import com.boticompara.usuario.entity.EstadoReserva;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S4-01 y S4-03 contra PostgreSQL: las consultas nuevas corren y el bloqueo sale como "for ... skip locked".
 * Solo lee: con ahora = 1970 ninguna reserva cumple venceEn <= ahora, así que no se bloquea ni se cambia nada, y la
 * transacción se deshace al final. Sin base configurada, la clase se omite. La tarea de H84 queda apagada.
 */
@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"app.reservas.vencer.habilitada=false", "spring.jpa.show-sql=true"})
class ReservaConsultasBaseTest {

    private static final Instant MIL_NOVECIENTOS_SETENTA = Instant.EPOCH;

    @Autowired
    ReservaRepository reservas;

    @Autowired
    UsuarioRepository usuarios;

    ReservaConsultasBaseTest() {
        BaseDePrueba.exigir();
    }

    @Test
    void bloquearVencidasUsaSkipLockedYNoTomaNadaDe1970(CapturedOutput log) {
        var vencidas = reservas.bloquearVencidas(EstadoReserva.ACTIVAS, MIL_NOVECIENTOS_SETENTA, PageRequest.of(0, 5));

        assertTrue(vencidas.isEmpty());
        assertTrue(log.getOut().toLowerCase().contains("skip locked"), "falta skip locked en la consulta");
    }

    @Test
    void lasConsultasDeReservasActivasCorren() {
        // Ids que no existen: solo se comprueba que el SQL es válido en PostgreSQL
        assertEquals(0L, reservas.unidadesApartadas(-1L, EstadoReserva.ACTIVAS, Instant.now()));
        assertEquals(0L, reservas.unidadesApartadasSinEsta(-1L, EstadoReserva.ACTIVAS, -1L, Instant.now()));
        assertEquals(false, reservas.existeActivaDelProducto(-1L, -1L, EstadoReserva.ACTIVAS, Instant.now()));
        assertEquals(0L, reservas.contarActivasDe(-1L, EstadoReserva.ACTIVAS, Instant.now()));
        assertTrue(usuarios.bloquearPorId(-1L).isEmpty());
    }
}
