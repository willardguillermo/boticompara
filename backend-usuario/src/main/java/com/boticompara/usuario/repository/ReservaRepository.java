package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    boolean existsByCodigo(String codigo);

    /** H76: historial del comprador, de la más reciente a la más antigua. */
    List<Reserva> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    List<Reserva> findByUsuarioIdAndEstadoOrderByCreadoEnDesc(Long usuarioId, EstadoReserva estado);

    /** H75, H76: solo las del propio comprador. Si es de otro, no existe. */
    Optional<Reserva> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Unidades que aparta un producto: cantidad disponible = stock - esto (API.md, sección 9). */
    @Query("select coalesce(sum(r.cantidad), 0) from Reserva r "
            + "where r.productoId = :productoId and r.estado in :estados")
    long unidadesApartadas(@Param("productoId") Long productoId,
                           @Param("estados") Collection<EstadoReserva> estados);

    /** Regla: un comprador no puede tener dos reservas activas del mismo producto (409). */
    boolean existsByUsuarioIdAndProductoIdAndEstadoIn(Long usuarioId, Long productoId,
                                                      Collection<EstadoReserva> estados);
}