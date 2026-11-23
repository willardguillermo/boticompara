package com.boticompara.usuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** H85-H87: mapea la tabla resena de v5/schema.sql. La respuesta del dueño (H88) no se toca aquí. */
@Entity
@Table(name = "resena")
@Getter
@Setter
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "botica_id", nullable = false)
    private Long boticaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private Short estrellas;

    @Column(length = 500)
    private String comentario;

    @Column(length = 500)
    private String respuesta;

    @Column(name = "respondida_en")
    private Instant respondidaEn;

    @Column(nullable = false)
    private boolean oculta = false;

    @Column(name = "motivo_ocultamiento")
    private String motivoOcultamiento;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }
}