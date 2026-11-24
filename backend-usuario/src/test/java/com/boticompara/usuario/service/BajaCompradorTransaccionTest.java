package com.boticompara.usuario.service;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * H19: el comprador elimina su cuenta. Con las transacciones y los eventos de Spring de verdad (se simulan la base, el
 * administrador de transacciones y el guardado del aviso): la cuenta y sus reservas se guardan en una sola transacción,
 * y la notificación RESERVA_CANCELADA de cada dueño (H103) se crea recién después del commit. Si algo falla, ninguna.
 */
@SpringJUnitConfig(BajaCompradorTransaccionTest.Config.class)
class BajaCompradorTransaccionTest {

    private static final long COMPRADOR = 10L;

    @Configuration
    @EnableTransactionManagement
    @Import({UsuarioService.class, ReservaService.class, NotificacionListener.class})
    static class Config {
        @Bean
        BajaDuenoTransaccionTest.Transacciones transacciones() {
            return new BajaDuenoTransaccionTest.Transacciones();
        }
    }

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean BoticaRepository boticas;
    @MockitoBean ProductoRepository productos;
    @MockitoBean TokenUsuarioRepository tokens;
    @MockitoBean FavoritoService favoritos;
    @MockitoBean AlertaService alertas;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean PedidoService pedidos;
    @MockitoBean ReservaRepository reservas;
    @MockitoBean BusquedaService busqueda;
    @MockitoBean CondicionVentaService condiciones;
    @MockitoBean NotificacionService notificaciones;

    @Autowired UsuarioService usuarioService;
    @Autowired BajaDuenoTransaccionTest.Transacciones transacciones;

    private final List<Reserva> activas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        transacciones.reiniciar();
        activas.clear();

        Usuario u = new Usuario();
        u.setId(COMPRADOR);
        u.setNombre("Carlos Ramírez");
        u.setCorreo("carlos@correo.test");
        u.setRol("COMPRADOR");
        u.setActivo(true);
        when(usuarios.findById(COMPRADOR)).thenReturn(Optional.of(u));
        when(encoder.encode(anyString())).thenReturn("HASH_INUTIL");
        when(boticas.bloquearPorUsuarioId(COMPRADOR)).thenReturn(Optional.empty());

        activas.add(reserva(15L, EstadoReserva.PENDIENTE));
        activas.add(reserva(16L, EstadoReserva.LISTA));
        when(reservas.bloquearPropiasConEstados(COMPRADOR, EstadoReserva.ACTIVAS)).thenReturn(activas);
        when(reservas.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        // Solo recibe el tipo y el id de la reserva: ningún dato del comprador
        doAnswer(i -> {
            transacciones.pasos.add("aviso " + i.getArgument(1));
            return null;
        }).when(notificaciones).crear(eq(TipoNotificacion.RESERVA_CANCELADA), any());
    }

    private Reserva reserva(long id, EstadoReserva estado) {
        Reserva r = new Reserva();
        r.setId(id);
        r.setCodigo("BC-00000" + id);
        r.setUsuarioId(COMPRADOR);
        r.setProductoId(1L);
        r.setCantidad(1);
        r.setPrecioUnitario(new BigDecimal("2.20"));
        r.setEstado(estado);
        r.setVenceEn(Instant.now().plus(1, ChronoUnit.DAYS));
        return r;
    }

    @Test
    void todoVaEnUnaTransaccionYLosAvisosSeCreanDespuesDelCommit() {
        usuarioService.eliminar(COMPRADOR);

        assertEquals(List.of("abre 1", "commit 1", "aviso 15", "aviso 16"), transacciones.pasos);
        for (Reserva r : activas) {
            assertEquals(EstadoReserva.CANCELADA, r.getEstado());
        }
    }

    @Test
    void siFallaAlGuardarUnaReservaSeDeshaceTodoYNoSeCreaNingunAviso() {
        when(reservas.save(any(Reserva.class)))
                .thenAnswer(i -> i.getArgument(0))
                .thenThrow(new IllegalStateException("falla la segunda"));

        assertThrows(IllegalStateException.class, () -> usuarioService.eliminar(COMPRADOR));

        assertEquals(List.of("abre 1", "solo rollback 1", "rollback 1"), transacciones.pasos);
    }
}
