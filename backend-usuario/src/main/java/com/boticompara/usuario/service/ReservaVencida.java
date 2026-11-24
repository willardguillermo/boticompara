package com.boticompara.usuario.service;

/** H84 (S4-01): se publica dentro de la transacción que pasa la reserva a VENCIDA. El aviso se guarda después del commit. */
public record ReservaVencida(Long reservaId) {
}
