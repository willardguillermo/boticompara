package com.boticompara.usuario.security;

/** Lo que Google confirma de la cuenta tras verificar el token (H67). */
public record GoogleIdentidad(String uid, String correo, String nombre, boolean correoVerificado) {
}