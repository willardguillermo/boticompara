package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    /** H19: el filtro JWT lo usa para dejar de aceptar los tokens de una cuenta eliminada. */
    boolean existsByIdAndActivoTrue(Long id);

    @Query("""
            select count(u) > 0 from Usuario u
            where u.id = :id and u.activo = true
              and (u.suspendidoHasta is null or u.suspendidoHasta <= :ahora)
            """)
    boolean puedeEntrar(@Param("id") Long id, @Param("ahora") java.time.Instant ahora);
}