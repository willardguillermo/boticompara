package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);
    Optional<Usuario> findByGoogleUid(String googleUid);

    /** H19: el filtro JWT lo usa para dejar de aceptar los tokens de una cuenta eliminada. */
    boolean existsByIdAndActivoTrue(Long id);

    @Query("""
            select count(u) > 0 from Usuario u
            where u.id = :id and u.activo = true
              and (u.suspendidoHasta is null or u.suspendidoHasta <= :ahora)
            """)
    boolean puedeEntrar(@Param("id") Long id, @Param("ahora") java.time.Instant ahora);

    /**
     * S4-03 (POST /reservas): bloquea la fila del comprador hasta el commit (select ... for update). Así dos reservas
     * suyas a la vez se esperan y la segunda ve a la primera al contar el máximo de reservas activas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> bloquearPorId(@Param("id") Long id);
}