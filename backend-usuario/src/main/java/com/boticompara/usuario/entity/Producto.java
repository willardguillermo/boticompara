package com.boticompara.usuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "producto")
@Getter
@Setter
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "botica_id", nullable = false)
    private Long boticaId;

    @Column(name = "nombre_comercial", nullable = false, length = 150)
    private String nombreComercial;

    @Column(name = "principio_activo", nullable = false, length = 150)
    private String principioActivo;

    @Column(nullable = false, length = 100)
    private String presentacion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer stock;

    /** Baja lógica: false = eliminado */
    @Column(nullable = false)
    private boolean activo = true;
}
