package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.PedidoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Reserva;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.ReservaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** H77-H81: pedidos del dueño (API.md, sección 10). */
@Service
@RequiredArgsConstructor
public class PedidoService {

    static final int LIMITE = 100;
    static final long HORAS_PARA_RECOGER = 48;
    static final int MOTIVO_MIN = 10;
    static final int MOTIVO_MAX = 300;

    private final BoticaRepository boticas;
    private final ProductoRepository productos;
    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;

    /** H77, H81: pedidos de la botica del dueño. estado vacío = todos. */
    @Transactional(readOnly = true)
    public List<PedidoResponse> listar(Long usuarioId, String estadoTexto) {
        Botica b = miBotica(usuarioId);
        PageRequest pagina = PageRequest.of(0, LIMITE);
        EstadoReserva estado = parsearOpcional(estadoTexto);
        List<Reserva> lista = estado == null
                ? reservas.pedidosDeBotica(b.getId(), pagina)
                : reservas.pedidosDeBoticaConEstado(b.getId(), estado, pagina);
        return respuestas(lista);
    }

    /** H78: detalle de un pedido de la botica. */
    @Transactional(readOnly = true)
    public PedidoResponse detalle(Long usuarioId, Long id) {
        return respuestas(List.of(pedidoDeMiBotica(usuarioId, id))).get(0);
    }

    /** H79: cambia el estado con PATCH /pedidos/{id}/estado. */
    @Transactional
    public PedidoResponse cambiarEstado(Long usuarioId, Long id, String estadoTexto) {
        EstadoReserva nuevo = parsear(estadoTexto);
        Reserva r = pedidoDeMiBotica(usuarioId, id);

        if (!r.getEstado().puedePasarPorEstadoA(nuevo)) {
            throw ApiException.conflict("No se puede pasar de " + r.getEstado() + " a " + nuevo);
        }
        if (venció(r)) {
            throw ApiException.conflict("La reserva ya venció");
        }

        switch (nuevo) {
            case CONFIRMADA -> validarStockAlConfirmar(r);
            case LISTA -> {
                r.setVenceEn(Instant.now().plus(HORAS_PARA_RECOGER, ChronoUnit.HOURS));
                r.setRecordatorioEnviado(false);
            }
            case ENTREGADA -> descontarStock(r);
            default -> throw ApiException.conflict("No se puede pasar de " + r.getEstado() + " a " + nuevo);
        }

        r.setEstado(nuevo);
        return respuestas(List.of(reservas.save(r))).get(0);
    }

    /** H80: rechaza un pedido PENDIENTE o CONFIRMADA, con motivo. */
    @Transactional
    public PedidoResponse rechazar(Long usuarioId, Long id, String motivoCrudo) {
        String motivo = motivoCrudo == null ? "" : motivoCrudo.trim();
        if (motivo.length() < MOTIVO_MIN || motivo.length() > MOTIVO_MAX) {
            throw ApiException.badRequest("El motivo debe tener entre " + MOTIVO_MIN + " y " + MOTIVO_MAX + " caracteres");
        }
        Reserva r = pedidoDeMiBotica(usuarioId, id);

        if (r.getEstado() != EstadoReserva.PENDIENTE && r.getEstado() != EstadoReserva.CONFIRMADA) {
            throw ApiException.conflict("No se puede rechazar un pedido " + r.getEstado());
        }
        if (venció(r)) {
            throw ApiException.conflict("La reserva ya venció");
        }

        r.setEstado(EstadoReserva.RECHAZADA);
        r.setMotivoRechazo(motivo);
        return respuestas(List.of(reservas.save(r))).get(0);
    }

    /**
     * Lo disponible para este pedido: stock menos las otras reservas activas, sin contar esta.
     * Nunca es negativo. Es el mismo cálculo que el campo disponible y que la validación al confirmar.
     */
    private long disponibleParaPedido(Producto p, Reserva r) {
        long otras = reservas.unidadesApartadasSinEsta(p.getId(), EstadoReserva.ACTIVAS, r.getId());
        return Math.max(0, p.getStock() - otras);
    }

    private void validarStockAlConfirmar(Reserva r) {
        Producto p = productos.bloquearPorId(r.getProductoId())
                .orElseThrow(() -> ApiException.notFound("El producto no existe"));
        if (!p.isActivo()) {
            throw ApiException.conflict("Este producto ya no está disponible en tu catálogo. "
                    + "Rechaza el pedido con un motivo.");
        }
        long disponible = disponibleParaPedido(p, r);
        if (disponible < r.getCantidad()) {
            throw ApiException.conflict("No hay stock suficiente para aceptar este pedido: disponible "
                    + disponible + ", pedido " + r.getCantidad() + ". Recházalo con un motivo.");
        }
    }

    /** H79: descuento atómico; si no alcanza, 409 y el pedido sigue en LISTA. */
    private void descontarStock(Reserva r) {
        int filas = productos.descontarStock(r.getProductoId(), r.getCantidad());
        if (filas == 0) {
            int quedan = productos.findById(r.getProductoId()).map(Producto::getStock).orElse(0);
            throw ApiException.conflict("Stock insuficiente: quedan " + quedan + " unidades");
        }
    }

    private Botica miBotica(Long usuarioId) {
        return boticas.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Todavía no registraste tu botica"));
    }

    private Reserva pedidoDeMiBotica(Long usuarioId, Long id) {
        Botica b = miBotica(usuarioId);
        return reservas.pedidoDeBotica(id, b.getId())
                .orElseThrow(() -> ApiException.notFound("El pedido no existe"));
    }

    private boolean venció(Reserva r) {
        return !r.getVenceEn().isAfter(Instant.now());
    }

    /**
     * Lee producto y comprador de sus tablas (no de la vista) en dos consultas, sin repetir por pedido.
     * disponible solo se calcula en PENDIENTE.
     */
    private List<PedidoResponse> respuestas(List<Reserva> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, Producto> prods = productos.findAllById(
                        lista.stream().map(Reserva::getProductoId).distinct().toList())
                .stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        Map<Long, Usuario> users = usuarios.findAllById(
                        lista.stream().map(Reserva::getUsuarioId).distinct().toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
        return lista.stream()
                .map(r -> {
                    Producto p = prods.get(r.getProductoId());
                    Usuario u = users.get(r.getUsuarioId());
                    Long disponible = (r.getEstado() == EstadoReserva.PENDIENTE && p != null)
                            ? disponibleParaPedido(p, r)
                            : null;
                    return PedidoResponse.from(r, p, u == null ? null : u.getNombre(), disponible);
                })
                .toList();
    }

    private EstadoReserva parsearOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : parsear(texto);
    }

    private EstadoReserva parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            throw ApiException.badRequest("El estado es obligatorio");
        }
        try {
            return EstadoReserva.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("El estado no es válido");
        }
    }
}