package com.boticompara.usuario.security;

/** Principal que se guarda en el SecurityContext a partir del JWT. */
public record UsuarioAutenticado(Long id, String rol) {
}
