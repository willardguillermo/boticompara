package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    /** H19: el filtro JWT lo usa para dejar de aceptar los tokens de una cuenta eliminada. */
    boolean existsByIdAndActivoTrue(Long id);
}