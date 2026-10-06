package com.boticompara.usuario.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RucValidatorTest {

    @Test
    void aceptaRucConDigitoVerificadorCorrecto() {
        assertTrue(RucValidator.esValido("20601234565"));
    }

    @Test
    void rechazaDigitoVerificadorIncorrecto() {
        assertFalse(RucValidator.esValido("20601234566"));
    }

    @Test
    void rechazaLongitudOCaracteresInvalidos() {
        assertFalse(RucValidator.esValido("123"));
        assertFalse(RucValidator.esValido("2060123456A"));
        assertFalse(RucValidator.esValido(null));
    }

    @Test
    void rechazaPrefijoNoPermitido() {
        assertFalse(RucValidator.esValido("30601234565"));
    }
}
