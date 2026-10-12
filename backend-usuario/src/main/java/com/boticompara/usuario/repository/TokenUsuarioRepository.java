package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.TokenUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {

    List<TokenUsuario> findByUsuarioIdAndTipoAndUsadoFalse(Long usuarioId, String tipo);
}