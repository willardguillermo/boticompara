package com.boticompara.usuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Códigos de recuperación (H17) y tokens de verificación de correo (H20). Tabla creada por database/v2_segunda_mitad.sql */
@Entity
@Table(name = "token_usuario")
@Getter
@Setter
public class TokenUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Nunca el código en claro: es un hash con clave (HMAC-SHA256). */
    @Column(nullable = false, length = 255)
    private String token;

    /** RECUPERACION o VERIFICACION */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private boolean usado = false;

    /** smallint en la base de datos */
    @Column(nullable = false)
    private short intentos = 0;
}