package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.Botica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoticaRepository extends JpaRepository<Botica, Long> {

    Optional<Botica> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);

    boolean existsByRuc(String ruc);

    List<Botica> findByEstado(String estado);
}