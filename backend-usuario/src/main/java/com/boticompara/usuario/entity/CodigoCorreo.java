package com.boticompara.usuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * H1, H20: código de 6 dígitos enviado al correo, y el tokenVerificacion que se entrega al confirmarlo (solo en el
 * registro). Sin usuario_id: en el registro la cuenta todavía no existe. Tabla creada por database/v9_verificacion_correo.sql
 */
@Entity
@Table(name = "codigo_correo")
@Getter
@Setter
public class CodigoCorreo {

    /** REGISTRO: antes de crear la cuenta. VERIFICACION: cuenta antigua sin verificar. */
    public enum Proposito { REGISTRO, VERIFICACION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** En minúsculas y sin espacios (check en la BD). */
    @Column(nullable = false, length = 150)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Proposito proposito;

    /** HMAC-SHA256 del código: nunca el código en claro. */
    @Column(name = "codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    /** smallint en la base de datos */
    @Column(nullable = false)
    private short intentos = 0;

    @Column(name = "vence_en", nullable = false)
    private Instant venceEn;

    @Column(name = "verificado_en")
    private Instant verificadoEn;

    /** HMAC-SHA256 del tokenVerificacion (solo REGISTRO). */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "token_vence_en")
    private Instant tokenVenceEn;

    /** Cuándo se gastó el token (se creó la cuenta). */
    @Column(name = "usado_en")
    private Instant usadoEn;

    /** Se pidió otro código para el mismo correo y propósito. */
    @Column(nullable = false)
    private boolean anulado = false;

    /** La pone la API (no el default de la BD) para contar con el mismo reloj que las demás fechas. */
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
