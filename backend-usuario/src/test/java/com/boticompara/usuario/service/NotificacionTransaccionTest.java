package com.boticompara.usuario.service;

import com.boticompara.usuario.controller.ReservaController;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Reserva;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.GlobalExceptionHandler;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.ReservaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.NotificacionService.DatosReserva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H103: el camino completo POST /reservas y PATCH /reservas/{id}/cancelar → commit → notificación, con las
 * transacciones y los eventos de Spring de verdad. Solo se simulan la base y el administrador de transacciones.
 */
@SpringJUnitConfig(NotificacionTransaccionTest.Config.class)
@ExtendWith(OutputCaptureExtension.class)
class NotificacionTransaccionTest {

    private static final long COMPRADOR = 10L;
    private static final long PRODUCTO = 1L;
    private static final long BOTICA = 2L;
    private static final long DUENO = 7L;
    private static final long RESERVA = 15L;
    private static final String DATO_DEL_COMPRADOR = "carlos@correo.test";

    @Configuration
    @EnableTransactionManagement
    @EnableAsync
    @Import({ReservaService.class, NotificacionService.class, NotificacionListener.class})
    static class Config {
        @Bean
        Transacciones transacciones() {
            return new Transacciones();
        }
    }

    /** Anota cada paso y permite hacer fallar la segunda transacción (la de la notificación). */
    static class Transacciones extends AbstractPlatformTransactionManager implements PlatformTransactionManager {
        final List<String> pasos = new ArrayList<>();
        boolean fallaAlAbrirLaSegunda;
        boolean fallaElCommitDeLaSegunda;
        private int abiertas;
        private Integer actual;

        void reiniciar() {
            pasos.clear();
            fallaAlAbrirLaSegunda = false;
            fallaElCommitDeLaSegunda = false;
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
            if (abiertas == 1 && fallaAlAbrirLaSegunda) {
                throw new CannotCreateTransactionException("sin conexiones para " + DATO_DEL_COMPRADOR);
            }
            actual = ++abiertas;
            pasos.add("abre " + actual);
        }

        @Override
        protected Object doSuspend(Object t) {
            Integer suspendida = actual;
            actual = null;
            return suspendida;
        }

        @Override
        protected void doResume(Object t, Object suspendida) {
            actual = (Integer) suspendida;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus s) {
            if (actual == 2 && fallaElCommitDeLaSegunda) {
                throw new TransactionSystemException("falló el commit " + DATO_DEL_COMPRADOR);
            }
            pasos.add("commit " + actual);
        }

        @Override
        protected void doRollback(DefaultTransactionStatus s) {
            pasos.add("rollback " + actual);
        }

        @Override
        protected void doCleanupAfterCompletion(Object t) {
            actual = null;
        }
    }

    @MockitoBean ProductoRepository productos;
    @MockitoBean BoticaRepository boticas;
    @MockitoBean ReservaRepository reservas;
    @MockitoBean BusquedaService busqueda;
    @MockitoBean CondicionVentaService condiciones;
    @MockitoBean NamedParameterJdbcTemplate jdbc;
    @MockitoBean UsuarioRepository usuarios;

    @Autowired ReservaService reservaService;
    @Autowired Transacciones transacciones;

    private final List<SqlParameterSource> guardadas = new ArrayList<>();
    private Thread hiloDelInsert;
    private RuntimeException errorAlGuardar;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        transacciones.reiniciar();

        Producto p = new Producto();
        p.setId(PRODUCTO);
        p.setBoticaId(BOTICA);
        p.setNombreComercial("Amoxil");
        p.setPresentacion("Cápsula 500 mg x 10");
        p.setPrecio(new BigDecimal("24.00"));
        p.setStock(5);
        Botica b = new Botica();
        b.setId(BOTICA);
        b.setUsuarioId(DUENO);
        Reserva pendiente = new Reserva();
        pendiente.setId(RESERVA);
        pendiente.setCodigo("BC-7K2QXM");
        pendiente.setUsuarioId(COMPRADOR);
        pendiente.setProductoId(PRODUCTO);
        pendiente.setCantidad(3);
        pendiente.setPrecioUnitario(new BigDecimal("24.00"));
        pendiente.setEstado(EstadoReserva.PENDIENTE);
        pendiente.setVenceEn(Instant.now().plus(1, ChronoUnit.DAYS));

        // El comprador ya verificó su correo: puede reservar
        Usuario comprador = new Usuario();
        comprador.setId(COMPRADOR);
        comprador.setCorreoVerificado(true);
        when(usuarios.findById(COMPRADOR)).thenReturn(Optional.of(comprador));
        when(productos.bloquearPorId(PRODUCTO)).thenReturn(Optional.of(p));
        when(productos.findById(PRODUCTO)).thenReturn(Optional.of(p));
        when(boticas.findById(BOTICA)).thenReturn(Optional.of(b));
        when(reservas.bloquearPropia(RESERVA, COMPRADOR)).thenReturn(Optional.of(pendiente));
        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            r.setId(RESERVA);
            return r;
        });
        // Lo que la consulta devuelve para la reserva 15: el dueño es el de la botica del producto
        when(jdbc.queryForObject(anyString(), ArgumentMatchers.<SqlParameterSource>argThat(
                q -> q != null && q.hasValue("reserva") && Long.valueOf(RESERVA).equals(q.getValue("reserva"))),
                ArgumentMatchers.<RowMapper<DatosReserva>>any()))
                .thenReturn(new DatosReserva(DUENO, "BC-7K2QXM", "Amoxil", 3, new BigDecimal("24.00"), "CON_RECETA"));
        when(jdbc.update(contains("insert into notificacion"), any(SqlParameterSource.class))).thenAnswer(inv -> {
            transacciones.pasos.add("insert");
            hiloDelInsert = Thread.currentThread();
            if (errorAlGuardar != null) {
                throw errorAlGuardar;
            }
            guardadas.add(inv.getArgument(1));
            return 1;
        });

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(COMPRADOR, "COMPRADOR"), null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new ReservaController(reservaService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ResultActions reservar() throws Exception {
        return mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productoId\": 1, \"cantidad\": 3}"));
    }

    private ResultActions cancelar() throws Exception {
        return mvc.perform(patch("/reservas/15/cancelar"));
    }

    private void reservarYCancelarRespondenIgual() throws Exception {
        reservar().andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").value(72.00));
        transaccionesDeNuevo();
        cancelar().andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.codigo").value("BC-7K2QXM"))
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
        assertTrue(guardadas.isEmpty() || transacciones.fallaElCommitDeLaSegunda);
    }

    /** Vuelve a contar desde la primera transacción, sin quitar el fallo configurado. */
    private void transaccionesDeNuevo() {
        boolean abrir = transacciones.fallaAlAbrirLaSegunda;
        boolean commit = transacciones.fallaElCommitDeLaSegunda;
        transacciones.reiniciar();
        transacciones.fallaAlAbrirLaSegunda = abrir;
        transacciones.fallaElCommitDeLaSegunda = commit;
    }

    private void assertGuardada(String tipo, String titulo, String mensaje) {
        assertEquals(1, guardadas.size());
        SqlParameterSource n = guardadas.get(0);
        assertEquals(DUENO, n.getValue("dueno"));
        assertEquals(tipo, n.getValue("tipo"));
        assertEquals(titulo, n.getValue("titulo"));
        assertEquals(mensaje, n.getValue("mensaje"));
        assertEquals(RESERVA, n.getValue("reserva"));
    }

    @Test
    void unaReservaCreaSuNuevaReservaParaElDuenoDeLaBotica() throws Exception {
        reservar().andExpect(status().isCreated());

        assertGuardada("NUEVA_RESERVA", "Nueva reserva BC-7K2QXM", "Amoxil x 3 · S/ 72.00 · Requiere receta médica");
    }

    @Test
    void unaCancelacionCreaSuReservaCanceladaParaElDuenoDeLaBotica() throws Exception {
        cancelar().andExpect(status().isOk());

        assertGuardada("RESERVA_CANCELADA", "Reserva cancelada BC-7K2QXM", "Amoxil x 3 · S/ 72.00");
    }

    @Test
    void seGuardaDespuesDelCommitEnOtraTransaccionYEnElMismoHilo() throws Exception {
        reservar();

        assertEquals(List.of("abre 1", "commit 1", "abre 2", "insert", "commit 2"), transacciones.pasos);
        assertSame(Thread.currentThread(), hiloDelInsert);
    }

    @Test
    void siLaReservaNoSeGuardaNoHayNotificacion() throws Exception {
        when(reservas.save(any(Reserva.class))).thenThrow(new DataAccessResourceFailureException("sin base"));

        reservar().andExpect(status().isInternalServerError());
        assertEquals(List.of("abre 1", "rollback 1"), transacciones.pasos);
    }

    @Test
    void siLaCancelacionSeRechazaNoHayNotificacion() throws Exception {
        cancelar().andExpect(status().isOk());
        guardadas.clear();

        cancelar().andExpect(status().isConflict());
        assertTrue(guardadas.isEmpty());
    }

    @Test
    void siFallaElInsertLaReservaYLaCancelacionRespondenIgualYElLogNoTraeDatosDelComprador(CapturedOutput log)
            throws Exception {
        errorAlGuardar = new DataAccessResourceFailureException("sin base " + DATO_DEL_COMPRADOR);

        reservarYCancelarRespondenIgual();

        assertTrue(log.getOut().contains("No se pudo guardar la notificación RESERVA_CANCELADA de la reserva 15"));
        assertFalse(log.getAll().contains(DATO_DEL_COMPRADOR));
        assertEquals(List.of("abre 1", "commit 1", "abre 2", "insert", "rollback 2"), transacciones.pasos);
    }

    @Test
    void siNoSePuedeAbrirLaTransaccionNuevaRespondenIgual(CapturedOutput log) throws Exception {
        transacciones.fallaAlAbrirLaSegunda = true;

        reservarYCancelarRespondenIgual();

        assertTrue(log.getOut().contains("de la reserva 15: CannotCreateTransactionException"));
        assertFalse(log.getAll().contains(DATO_DEL_COMPRADOR));
    }

    @Test
    void siFallaElCommitDeLaNotificacionRespondenIgual(CapturedOutput log) throws Exception {
        transacciones.fallaElCommitDeLaSegunda = true;

        reservarYCancelarRespondenIgual();

        assertTrue(log.getOut().contains("de la reserva 15: TransactionSystemException"));
        assertFalse(log.getAll().contains(DATO_DEL_COMPRADOR));
    }

    @Test
    void unDuplicadoSeIgnora(CapturedOutput log) throws Exception {
        errorAlGuardar = new DuplicateKeyException("uq_notificacion_reserva_tipo");

        reservarYCancelarRespondenIgual();

        assertTrue(log.getOut().contains("La notificación NUEVA_RESERVA de la reserva 15 ya existía"));
        assertFalse(log.getOut().contains("No se pudo guardar"));
    }
}
