package com.boticompara.usuario.dto;

public record LoginResponse(String token, String tipo, UsuarioResponse usuario) {
}
