package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.Usuario;

public record PerfilResponse(Long id, String nombre, String correo, String telefono, String direccion, String rol) {

    public static PerfilResponse from(Usuario u) {
        return new PerfilResponse(u.getId(), u.getNombre(), u.getCorreo(), u.getTelefono(), u.getDireccion(), u.getRol());
    }
}
