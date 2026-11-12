package com.boticompara.usuario.chat;

/** H68: mensaje anterior de la conversación. rol es "usuario" o "asistente". */
public record MensajeHistorial(String rol, String texto) {
}
