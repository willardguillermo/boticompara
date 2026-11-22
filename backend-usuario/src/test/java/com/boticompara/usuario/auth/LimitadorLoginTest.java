package com.boticompara.usuario.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LimitadorLoginTest {

    @Test
    void elQuintoFalloBloqueaYElSextoResponde429ConSegundos() {
        LimitadorLogin l = new LimitadorLogin();
        for (int i = 0; i < 5; i++) {
            l.registrarFallo("ana@correo.com", "1.1.1.1");
        }
        LimiteLoginException ex = assertThrows(LimiteLoginException.class,
                () -> l.verificarNoBloqueado("ana@correo.com", "1.1.1.1"));
        assertEquals(true, ex.getReintentarEnSegundos() > 800 && ex.getReintentarEnSegundos() <= 900);
    }

    @Test
    void otraIpOOtroCorreoNoQuedanBloqueados() {
        LimitadorLogin l = new LimitadorLogin();
        for (int i = 0; i < 5; i++) {
            l.registrarFallo("ana@correo.com", "1.1.1.1");
        }
        assertDoesNotThrow(() -> l.verificarNoBloqueado("ana@correo.com", "2.2.2.2"));
        assertDoesNotThrow(() -> l.verificarNoBloqueado("otra@correo.com", "1.1.1.1"));
    }

    @Test
    void unLoginCorrectoBorraLosFallosPrevios() {
        LimitadorLogin l = new LimitadorLogin();
        for (int i = 0; i < 4; i++) {
            l.registrarFallo("ana@correo.com", "1.1.1.1");
        }
        l.limpiar("ana@correo.com", "1.1.1.1");
        for (int i = 0; i < 4; i++) {
            l.registrarFallo("ana@correo.com", "1.1.1.1");
        }
        assertDoesNotThrow(() -> l.verificarNoBloqueado("ana@correo.com", "1.1.1.1"));
    }

    @Test
    void elCorreoSeComparaSinMayusculas() {
        LimitadorLogin l = new LimitadorLogin();
        for (int i = 0; i < 5; i++) {
            l.registrarFallo("Ana@Correo.com", "1.1.1.1");
        }
        assertThrows(LimiteLoginException.class,
                () -> l.verificarNoBloqueado("ana@correo.com", "1.1.1.1"));
    }
}