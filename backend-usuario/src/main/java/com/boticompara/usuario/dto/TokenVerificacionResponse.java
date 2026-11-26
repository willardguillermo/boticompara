package com.boticompara.usuario.dto;

/** H20: respuesta de POST /auth/registro/verificar. Sirve para crear una sola cuenta y vence a los 30 minutos. */
public record TokenVerificacionResponse(String tokenVerificacion) {
}
