package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.ReservaResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Reserva;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** H72-H76: reservas del comprador (API.md, sección 9). */
@Service
@RequiredArgsConstructor
public class ReservaService {

    static final int CANTIDAD_MAXIMA = 10;
    static final long HORAS_DE_PLAZO = 24;

    /** Sin 0, O, 1 ni I, para que el código no se confunda al dictarlo. */
    private static final String ALFABETO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int INTENTOS_CODIGO = 3;

    private final SecureRandom azar = new SecureRandom();

    private final ProductoRepository productos;
    private final BoticaRepository boticas;
    private final ReservaRepository reservas;
    private final BusquedaService busqueda;

    /**
     * H72-H74: crea la reserva en PENDIENTE. El producto se bloquea dentro de la transacción,
     * así dos compradores no pueden tomar las mismas unidades a la vez.
     */
    @Transactional
    public ReservaResponse crear(Long usuarioId, Long productoId, Integer cantidad) {
        if (cantidad == null || cantidad < 1 || cantidad > CANTIDAD_MAXIMA) {
            throw ApiException.badRequest("La cantidad debe estar entre 1 y " + CANTIDAD_MAXIMA);
        }

        // Misma visibilidad que GET /productos/{id}: si no se ve, no se puede reservar
        try {
            busqueda.detalle(productoId);
        } catch (ApiException e) {
            throw ApiException.notFound("El producto no está disponible");
        }

        Producto p = productos.bloquearPorId(productoId)
                .orElseThrow(() -> ApiException.notFound("El producto no está disponible"));

        if (reservas.existsByUsuarioIdAndProductoIdAndEstadoIn(usuarioId, productoId, EstadoReserva.ACTIVAS)) {
            throw ApiException.conflict("Ya tienes una reserva activa de este producto");
        }

        long disponible = p.getStock() - reservas.unidadesApartadas(productoId, EstadoReserva.ACTIVAS);
        if (disponible <= 0) {
            throw ApiException.conflict("Todas las unidades de este producto ya están reservadas");
        }
        if (cantidad > disponible) {
            throw ApiException.conflict("Solo hay " + disponible + " unidades disponibles de este producto");
        }

        Reserva r = new Reserva();
        r.setCodigo(nuevoCodigo());
        r.setUsuarioId(usuarioId);
        r.setProductoId(productoId);
        r.setCantidad(cantidad);
        r.setPrecioUnitario(p.getPrecio());
        r.setEstado(EstadoReserva.PENDIENTE);
        r.setVenceEn(Instant.now().plus(HORAS_DE_PLAZO, ChronoUnit.HOURS));

        Reserva guardada = reservas.save(r);
        return ReservaResponse.from(guardada, p, boticas.findById(p.getBoticaId()).orElse(null));
    }

    /** H76: historial del comprador. estado = null devuelve todas. */
    @Transactional(readOnly = true)
    public List<ReservaResponse> misReservas(Long usuarioId, EstadoReserva estado) {
        List<Reserva> lista = estado == null
                ? reservas.findTop100ByUsuarioIdOrderByCreadoEnDesc(usuarioId)
                : reservas.findTop100ByUsuarioIdAndEstadoOrderByCreadoEnDesc(usuarioId, estado);

        // Producto y botica se leen de sus tablas, no de la vista: la reserva sigue visible aunque el producto ya no se vea
        List<Long> idsProductos = lista.stream().map(Reserva::getProductoId).distinct().toList();
        Map<Long, Producto> prods = productos.findAllById(idsProductos).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        List<Long> idsBoticas = prods.values().stream().map(Producto::getBoticaId).distinct().toList();
        Map<Long, Botica> bots = boticas.findAllById(idsBoticas).stream()
                .collect(Collectors.toMap(Botica::getId, Function.identity()));

        return lista.stream()
                .map(r -> {
                    Producto p = prods.get(r.getProductoId());
                    Botica b = p == null ? null : bots.get(p.getBoticaId());
                    return ReservaResponse.from(r, p, b);
                })
                .toList();
    }

    /** H72: BC- + 6 caracteres del alfabeto, sin repetir (hasta 3 intentos). */
    private String nuevoCodigo() {
        for (int i = 0; i < INTENTOS_CODIGO; i++) {
            StringBuilder sb = new StringBuilder("BC-");
            for (int j = 0; j < 6; j++) {
                sb.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
            }
            String codigo = sb.toString();
            if (!reservas.existsByCodigo(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de reserva único");
    }
}