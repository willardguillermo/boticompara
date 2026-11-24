package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Reserva;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.ReservaRepository;
import com.boticompara.usuario.repository.TokenUsuarioRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * S4-02: el dueño elimina su cuenta. Con las transacciones y los eventos de Spring de verdad (solo se simulan la base y
 * el administrador de transacciones): la cuenta, la botica y las reservas se guardan en una sola transacción, y los
 * correos a los compradores salen recién después del commit. Si algo falla, no sale ningún correo.
 */
@SpringJUnitConfig(BajaDuenoTransaccionTest.Config.class)
class BajaDuenoTransaccionTest {

    private static final long DUENO = 7L;
    private static final long BOTICA = 2L;

    @Configuration
    @EnableTransactionManagement
    @Import({UsuarioService.class, PedidoService.class, PedidoCorreoService.class})
    static class Config {
        @Bean
        Transacciones transacciones() {
            return new Transacciones();
        }
    }

    /** Anota cada transacción que se abre, confirma o deshace, y cada correo que se prepara. */
    static class Transacciones extends AbstractPlatformTransactionManager implements PlatformTransactionManager {
        final List<String> pasos = new ArrayList<>();
        private int abiertas;
        private Integer actual;

        void reiniciar() {
            pasos.clear();
            abiertas = 0;
            actual = null;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object t) {
            return actual != null;
        }

        @Override
        protected void doBegin(Object t, TransactionDefinition definicion) {
            actual = ++abiertas;
            pasos.add("abre " + actual);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus s) {
            pasos.add("commit " + actual);
        }

        @Override
        protected void doRollback(DefaultTransactionStatus s) {
            pasos.add("rollback " + actual);
        }

        /** PedidoService participa en la misma transacción: si falla, la deja marcada para deshacerse. */
        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus s) {
            pasos.add("solo rollback " + actual);
        }

        @Override
        protected void doCleanupAfterCompletion(Object t) {
            actual = null;
        }
    }

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean BoticaRepository boticas;
    @MockitoBean ProductoRepository productos;
    @MockitoBean TokenUsuarioRepository tokens;
    @MockitoBean FavoritoService favoritos;
    @MockitoBean AlertaService alertas;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean ReservaRepository reservas;
    @MockitoBean CondicionVentaService condiciones;
    @MockitoBean NamedParameterJdbcTemplate jdbc;
    @MockitoBean CorreoService correo;

    @Autowired UsuarioService usuarioService;
    @Autowired Transacciones transacciones;

    private Botica botica;
    private final List<Reserva> activas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        transacciones.reiniciar();
        activas.clear();

        Usuario u = new Usuario();
        u.setId(DUENO);
        u.setNombre("Rosa Quispe");
        u.setCorreo("rosa@correo.test");
        u.setRol("DUENO_BOTICA");
        u.setActivo(true);
        when(usuarios.findById(DUENO)).thenReturn(Optional.of(u));
        when(encoder.encode(anyString())).thenReturn("HASH_INUTIL");

        botica = new Botica();
        botica.setId(BOTICA);
        botica.setUsuarioId(DUENO);
        botica.setEstado("APROBADO");
        when(boticas.bloquearPorUsuarioId(DUENO)).thenReturn(Optional.of(botica));

        activas.add(reserva(15L, EstadoReserva.PENDIENTE));
        activas.add(reserva(16L, EstadoReserva.CONFIRMADA));
        when(reservas.bloquearDeBoticaConEstados(BOTICA, EstadoReserva.ACTIVAS)).thenReturn(activas);
        when(reservas.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        // Cada correo de rechazo (H83) empieza leyendo los datos de su reserva
        doAnswer(i -> {
            SqlParameterSource p = i.getArgument(1);
            transacciones.pasos.add("correo " + ((MapSqlParameterSource) p).getValue("id"));
            return null;
        }).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
    }

    private Reserva reserva(long id, EstadoReserva estado) {
        Reserva r = new Reserva();
        r.setId(id);
        r.setCodigo("BC-00000" + id);
        r.setUsuarioId(10L);
        r.setProductoId(1L);
        r.setCantidad(1);
        r.setPrecioUnitario(new BigDecimal("2.20"));
        r.setEstado(estado);
        r.setVenceEn(Instant.now().plus(1, ChronoUnit.DAYS));
        return r;
    }

    @Test
    void todoVaEnUnaTransaccionYLosCorreosSalenDespuesDelCommit() {
        usuarioService.eliminar(DUENO);

        assertEquals(List.of("abre 1", "commit 1", "correo 15", "correo 16"), transacciones.pasos);
        assertEquals("RECHAZADO", botica.getEstado());
        assertEquals("Cuenta eliminada por el dueño", botica.getMotivoRechazo());
        for (Reserva r : activas) {
            assertEquals(EstadoReserva.RECHAZADA, r.getEstado());
            assertEquals("La botica dejó de atender en BotiCompara", r.getMotivoRechazo());
        }
    }

    @Test
    void siFallaAlGuardarUnaReservaSeDeshaceTodoYNoSaleNingunCorreo() {
        when(reservas.save(any(Reserva.class)))
                .thenAnswer(i -> i.getArgument(0))
                .thenThrow(new IllegalStateException("falla la segunda"));

        assertThrows(IllegalStateException.class, () -> usuarioService.eliminar(DUENO));

        assertEquals(List.of("abre 1", "solo rollback 1", "rollback 1"), transacciones.pasos);
    }
}
