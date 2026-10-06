package com.boticompara.usuario.repository;

import com.boticompara.usuario.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(Long boticaId);

    /** patron ya viene en minúsculas y con %...% */
    @Query("""
            select p from Producto p
            where p.boticaId = :boticaId and p.activo = true
              and (lower(p.nombreComercial) like :patron or lower(p.principioActivo) like :patron)
            order by p.nombreComercial asc
            """)
    List<Producto> buscarEnBotica(@Param("boticaId") Long boticaId, @Param("patron") String patron);

    Optional<Producto> findByIdAndBoticaIdAndActivoTrue(Long id, Long boticaId);
}
