package com.boticompara.usuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "botica")
@Getter
@Setter
public class Botica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private Long usuarioId;

    @Column(name = "nombre_comercial", nullable = false, length = 150)
    private String nombreComercial;

    @Column(nullable = false, length = 11, unique = true)
    private String ruc;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(nullable = false)
    private String direccion;

    @Column(nullable = false, length = 100)
    private String distrito;

    @Column(length = 20)
    private String telefono;

    /** PENDIENTE, APROBADO o RECHAZADO (lo cambia el admin desde Django) */
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "motivo_rechazo", columnDefinition = "text")
    private String motivoRechazo;
}
