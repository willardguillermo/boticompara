package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CodigoCorreoRepository extends JpaRepository<CodigoCorreo, Long> {

    /** El último código de un correo y propósito (el único que puede servir). */
    Optional<CodigoCorreo> findFirstByCorreoAndPropositoOrderByIdDesc(String correo, Proposito proposito);

    /** Los códigos enviados desde cierto momento, del más antiguo al más nuevo: límite de 5 por hora. */
    List<CodigoCorreo> findByCorreoAndPropositoAndCreadoEnAfterOrderByIdAsc(String correo, Proposito proposito,
                                                                          Instant desde);

    /** validar-ruc del registro: solo lee. */
    Optional<CodigoCorreo> findByTokenHash(String tokenHash);

    /**
     * POST /auth/registro: bloquea la fila del token hasta el commit (select ... for update). Dos registros a la vez
     * con el mismo token se esperan, y el segundo ve el token ya gastado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodigoCorreo c where c.tokenHash = :tokenHash")
    Optional<CodigoCorreo> bloquearPorTokenHash(@Param("tokenHash") String tokenHash);

    /** Pedir un código nuevo anula los anteriores de ese correo y propósito que no se gastaron (y sus tokens). */
    @Modifying
    @Query("""
            update CodigoCorreo c set c.anulado = true
            where c.correo = :correo and c.proposito = :proposito and c.anulado = false and c.usadoEn is null
            """)
    int anularPendientes(@Param("correo") String correo, @Param("proposito") Proposito proposito);

    /**
     * Serializa los pedidos de código de un mismo correo hasta el commit (bloqueo de PostgreSQL por transacción),
     * así dos pedidos a la vez no se saltan los 60 s ni el máximo por hora.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:clave))) x", nativeQuery = true)
    Integer bloquearCorreo(@Param("clave") String clave);

    /** Limpieza: pasado un día, ninguna fila sirve (el código vive 15 min y el token 30 más). */
    @Modifying
    @Query("delete from CodigoCorreo c where c.creadoEn < :limite")
    int borrarCreadosAntesDe(@Param("limite") Instant limite);
}
