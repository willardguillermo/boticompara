package com.boticompara.usuario.service;

/** H103: tipos que crea la API. RESERVA_VENCIDA ya está en la base, pero la generará la tarea de H84. */
enum TipoNotificacion {

    NUEVA_RESERVA("Nueva reserva"),
    RESERVA_CANCELADA("Reserva cancelada");

    private final String titulo;

    TipoNotificacion(String titulo) {
        this.titulo = titulo;
    }

    String titulo(String codigo) {
        return titulo + " " + codigo;
    }
}
