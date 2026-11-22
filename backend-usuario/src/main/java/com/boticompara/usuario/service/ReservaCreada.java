package com.boticompara.usuario.service;

/** H82: se publica dentro de la transacción de POST /reservas. El correo sale después del commit. */
public record ReservaCreada(Long reservaId) {
}