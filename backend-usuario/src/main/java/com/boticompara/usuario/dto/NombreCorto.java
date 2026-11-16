package com.boticompara.usuario.dto;

import java.util.Locale;

/**
 * Regla de API.md (secciones 10 y 11): nombre de pila y la inicial del apellido.
 * "Carlos Ramírez" -> "Carlos R.". Una sola palabra se devuelve tal cual.
 * La cuenta eliminada (H19) se muestra como "Usuario eliminado".
 */
public final class NombreCorto {

    static final String ELIMINADO = "Usuario eliminado";

    private NombreCorto() {
    }

    public static String de(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank() || ELIMINADO.equals(nombreCompleto.trim())) {
            return ELIMINADO;
        }
        String[] partes = nombreCompleto.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        return partes[0] + " " + partes[1].substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }
}