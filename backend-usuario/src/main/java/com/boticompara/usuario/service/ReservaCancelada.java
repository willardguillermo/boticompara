package com.boticompara.usuario.service;

/** H103: se publica dentro de la transacción de PATCH /reservas/{id}/cancelar. El aviso al dueño se guarda después del commit. */
public record ReservaCancelada(Long reservaId) {
}
