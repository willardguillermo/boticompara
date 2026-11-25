package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.Botica;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoticaRepository extends JpaRepository<Botica, Long> {

    Optional<Botica> findByUsuarioId(Long usuarioId);

    /**
     * S4-02 (baja del dueño): bloquea la fila hasta el commit. El admin de Django también la bloquea al revisar
     * (select_for_update), así una aprobación a la vez no pisa el RECHAZADO ni al revés.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Botica b where b.usuarioId = :usuarioId")
    Optional<Botica> bloquearPorUsuarioId(@Param("usuarioId") Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);

    /** A5: el RUC solo choca con una botica APROBADO. */
    boolean existsByRucAndEstado(String ruc, String estado);

    List<Botica> findByEstado(String estado);
}