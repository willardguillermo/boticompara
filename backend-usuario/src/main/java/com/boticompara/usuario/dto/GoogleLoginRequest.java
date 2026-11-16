package com.boticompara.usuario.dto;

/** H67: body de POST /auth/google. La validación de vacío la hace el verificador (400). */
public record GoogleLoginRequest(String idToken) {
}