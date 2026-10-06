package com.boticompara.usuario.util;

import com.boticompara.usuario.dto.RucResponse;

import java.util.Set;

/** Validación de formato y dígito verificador del RUC (sin servicios externos). H2 */
public final class RucValidator {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final Set<String> PREFIJOS = Set.of("10", "15", "17", "20");

    private RucValidator() {
    }

    public static boolean esValido(String ruc) {
        return validar(ruc).valido();
    }

    public static RucResponse validar(String ruc) {
        if (ruc == null || !ruc.matches("[0-9]{11}")) {
            return new RucResponse(ruc, false, "El RUC debe tener 11 dígitos numéricos");
        }
        if (!PREFIJOS.contains(ruc.substring(0, 2))) {
            return new RucResponse(ruc, false, "El RUC debe empezar con 10, 15, 17 o 20");
        }
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += Character.digit(ruc.charAt(i), 10) * PESOS[i];
        }
        int digito = 11 - (suma % 11);
        if (digito == 10) {
            digito = 0;
        } else if (digito == 11) {
            digito = 1;
        }
        if (digito != Character.digit(ruc.charAt(10), 10)) {
            return new RucResponse(ruc, false, "El dígito verificador del RUC no es válido");
        }
        return new RucResponse(ruc, true, "RUC válido");
    }
}
