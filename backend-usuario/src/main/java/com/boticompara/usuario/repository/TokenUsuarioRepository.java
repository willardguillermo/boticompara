package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.TokenUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {

    List<TokenUsuario> findByUsuarioIdAndTipoAndUsadoFalse(Long usuarioId, String tipo);

    /** H20: el token de verificación es largo y aleatorio, así que se busca directamente por su hash. */
    Optional<TokenUsuario> findByTokenAndTipo(String token, String tipo);
}