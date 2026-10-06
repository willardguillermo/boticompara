package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.Usuario;

public record UsuarioResponse(Long id, String nombre, String correo, String rol) {

    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getCorreo(), u.getRol());
    }
}
