package com.boticompara.usuario.dto;

import com.boticompara.usuario.entity.Usuario;

/** H16. D5: correoVerificado indica si el usuario ya abrió el enlace de verificación (H20). */
public record PerfilResponse(Long id, String nombre, String correo, String telefono, String direccion, String rol,
                             boolean correoVerificado) {

    public static PerfilResponse from(Usuario u) {
        return new PerfilResponse(u.getId(), u.getNombre(), u.getCorreo(), u.getTelefono(), u.getDireccion(), u.getRol(),
                u.isCorreoVerificado());
    }
}