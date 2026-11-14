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

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_uid", length = 128, unique = true)
    private String googleUid;

    @Column(name = "suspendido_hasta")
    private java.time.Instant suspendidoHasta;

    @Column(name = "motivo_estado")
    private String motivoEstado;

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

    /** H91: suspendida si tiene una fecha de suspensión en el futuro. */
    public boolean estaSuspendido() {
        return suspendidoHasta != null && suspendidoHasta.isAfter(java.time.Instant.now());
    }

    /** H66: si quiere recibir alertas de baja de precio. Por defecto true (igual que en la BD). */
    @Column(name = "notificaciones_precio", nullable = false)
    private boolean notificacionesPrecio = true;
}
