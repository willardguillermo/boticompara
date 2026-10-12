package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.TokenUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {

    List<TokenUsuario> findByUsuarioIdAndTipoAndUsadoFalse(Long usuarioId, String tipo);

    /** H20: el token de verificación es largo y aleatorio, así que se busca directamente por su hash. */
    Optional<TokenUsuario> findByTokenAndTipo(String token, String tipo);

    /** H19: deja sin efecto todos los códigos y tokens pendientes de un usuario. */
    @Modifying
    @Query("update TokenUsuario t set t.usado = true where t.usuarioId = :usuarioId and t.usado = false")
    int invalidarPendientes(@Param("usuarioId") Long usuarioId);
}