package com.boticompara.usuario.service;

/** H103: tipos que crea la API. RESERVA_VENCIDA la genera la tarea de H84 (VencerReservasJob). */
enum TipoNotificacion {

    NUEVA_RESERVA("Nueva reserva"),
    RESERVA_CANCELADA("Reserva cancelada"),
    RESERVA_VENCIDA("Reserva vencida");

    private final String titulo;

    TipoNotificacion(String titulo) {
        this.titulo = titulo;
    }

    String titulo(String codigo) {
        return titulo + " " + codigo;
    }
}
