package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.Lock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    boolean existsByCodigo(String codigo);

    /** H76: historial del comprador, de la más reciente a la más antigua. */
    /** H76: historial del comprador, de la más reciente a la más antigua, máximo 100. */
    List<Reserva> findTop100ByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    List<Reserva> findTop100ByUsuarioIdAndEstadoOrderByCreadoEnDesc(Long usuarioId, EstadoReserva estado);
    /** H75, H76: solo las del propio comprador. Si es de otro, no existe. */
    Optional<Reserva> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Unidades que aparta un producto: cantidad disponible = stock - esto (API.md, sección 9).
     * S4-01: una reserva con venceEn pasado ya no aparta, aunque H84 todavía no la haya marcado VENCIDA.
     */
    @Query("select coalesce(sum(r.cantidad), 0) from Reserva r "
            + "where r.productoId = :productoId and r.estado in :estados and r.venceEn > :ahora")
    long unidadesApartadas(@Param("productoId") Long productoId,
                           @Param("estados") Collection<EstadoReserva> estados,
                           @Param("ahora") Instant ahora);

    /** Regla: un comprador no puede tener dos reservas activas del mismo producto (409). Las vencidas no cuentan. */
    @Query("select count(r) > 0 from Reserva r where r.usuarioId = :usuarioId and r.productoId = :productoId "
            + "and r.estado in :estados and r.venceEn > :ahora")
    boolean existeActivaDelProducto(@Param("usuarioId") Long usuarioId, @Param("productoId") Long productoId,
                                    @Param("estados") Collection<EstadoReserva> estados,
                                    @Param("ahora") Instant ahora);

    /** S4-03: reservas activas sin vencer del comprador, entre todos los productos. */
    @Query("select count(r) from Reserva r where r.usuarioId = :usuarioId "
            + "and r.estado in :estados and r.venceEn > :ahora")
    long contarActivasDe(@Param("usuarioId") Long usuarioId,
                         @Param("estados") Collection<EstadoReserva> estados,
                         @Param("ahora") Instant ahora);

    /**
     * H84 (S4-01): reservas activas con venceEn pasado, de la más antigua a la más nueva, bloqueadas hasta el commit.
     * El timeout -2 es SKIP LOCKED en Hibernate: salta las que otra transacción está cambiando (un dueño confirmando,
     * un comprador cancelando); esas ya responden 409 "La reserva ya venció" y vencen en la siguiente ejecución.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select r from Reserva r where r.estado in :estados and r.venceEn <= :ahora order by r.venceEn asc, r.id asc")
    List<Reserva> bloquearVencidas(@Param("estados") Collection<EstadoReserva> estados,
                                   @Param("ahora") Instant ahora, Pageable pagina);

    /** H77, H81: pedidos de una botica, de la más reciente a la más antigua. */
    @Query("select r from Reserva r, Producto p "
            + "where r.productoId = p.id and p.boticaId = :boticaId "
            + "order by r.creadoEn desc")
    List<Reserva> pedidosDeBotica(@Param("boticaId") Long boticaId, Pageable pagina);

    /** H81: pedidos de una botica filtrados por estado. */
    @Query("select r from Reserva r, Producto p "
            + "where r.productoId = p.id and p.boticaId = :boticaId and r.estado = :estado "
            + "order by r.creadoEn desc")
    List<Reserva> pedidosDeBoticaConEstado(@Param("boticaId") Long boticaId,
                                           @Param("estado") EstadoReserva estado,
                                           Pageable pagina);

    /** H78-H80: solo si el pedido es de esa botica. Si no, no existe. */
    @Query("select r from Reserva r, Producto p "
            + "where r.id = :id and r.productoId = p.id and p.boticaId = :boticaId")
    Optional<Reserva> pedidoDeBotica(@Param("id") Long id, @Param("boticaId") Long boticaId);

    /** H79: unidades apartadas por las OTRAS reservas activas sin vencer del producto (validación al confirmar). */
    @Query("select coalesce(sum(r.cantidad), 0) from Reserva r "
            + "where r.productoId = :productoId and r.estado in :estados and r.id <> :id and r.venceEn > :ahora")
    long unidadesApartadasSinEsta(@Param("productoId") Long productoId,
                                  @Param("estados") Collection<EstadoReserva> estados,
                                  @Param("id") Long id,
                                  @Param("ahora") Instant ahora);


    /** H78-H80 (cambios): bloquea la fila de la reserva hasta el commit, solo si es de esa botica. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r, Producto p "
            + "where r.id = :id and r.productoId = p.id and p.boticaId = :boticaId")
    Optional<Reserva> bloquearPedidoDeBotica(@Param("id") Long id, @Param("boticaId") Long boticaId);

    /** S4-02: bloquea las reservas de una botica con esos estados, en orden de id (como lo haría otro cambio). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r, Producto p "
            + "where r.productoId = p.id and p.boticaId = :boticaId and r.estado in :estados order by r.id")
    List<Reserva> bloquearDeBoticaConEstados(@Param("boticaId") Long boticaId,
                                            @Param("estados") Collection<EstadoReserva> estados);

    /** H19 (comprador que elimina su cuenta): bloquea sus reservas con esos estados, en orden de id. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.usuarioId = :usuarioId and r.estado in :estados order by r.id")
    List<Reserva> bloquearPropiasConEstados(@Param("usuarioId") Long usuarioId,
                                           @Param("estados") Collection<EstadoReserva> estados);

    /** H75 (cancelar): bloquea la reserva del comprador hasta el commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id and r.usuarioId = :usuarioId")
    Optional<Reserva> bloquearPropia(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /** Unidades comprometidas de un producto: suma de CONFIRMADA y LISTA (API.md, sección 4). */
    @Query("select coalesce(sum(r.cantidad), 0) from Reserva r "
            + "where r.productoId = :productoId and r.estado in :estados")
    long unidadesComprometidas(@Param("productoId") Long productoId,
                               @Param("estados") Collection<EstadoReserva> estados);

    /** Baja de un producto: hay pedidos confirmados o listos (API.md, sección 4). */
    boolean existsByProductoIdAndEstadoIn(Long productoId, Collection<EstadoReserva> estados);

    /** Regla de API.md, sección 11: el comprador solo reseña si recogió una reserva ENTREGADA de esa botica. */
    @Query("select count(r) > 0 from Reserva r, Producto p "
            + "where r.usuarioId = :usuarioId and r.productoId = p.id and p.boticaId = :boticaId "
            + "and r.estado = com.boticompara.usuario.entity.EstadoReserva.ENTREGADA")
    boolean hayEntregadaDe(@Param("usuarioId") Long usuarioId, @Param("boticaId") Long boticaId);
}