package com.boticompara.usuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 150, unique = true)
    private String correo;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 20)
    private String telefono;

    private String direccion;

    /** COMPRADOR o DUENO_BOTICA */
    @Column(nullable = false, length = 20)
    private String rol;

    @Column(name = "correo_verificado", nullable = false)
    private boolean correoVerificado = false;

    @Column(nullable = false)
    private boolean activo = true;
}
