package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.Resena;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResenaRepository extends JpaRepository<Resena, Long> {

    Optional<Resena> findByUsuarioIdAndBoticaId(Long usuarioId, Long boticaId);

    /** H87: solo las no ocultas, paginadas (la paginación ordena por creadoEn descendente). */
    Page<Resena> findByBoticaIdAndOcultaFalse(Long boticaId, Pageable pagina);

    /** H87: suma de estrellas y total de las no ocultas (el servicio calcula el promedio exacto). */
    @Query("select coalesce(sum(r.estrellas), 0), count(r) from Resena r where r.boticaId = :boticaId and r.oculta = false")
    List<Object[]> resumen(@Param("boticaId") Long boticaId);
}