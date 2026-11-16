package com.boticompara.usuario.security;

import com.boticompara.usuario.error.ApiException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebaseVerificadorGoogleTest {

    private FirebaseToken token(String correo, Object emailVerified, String nombre) {
        FirebaseToken t = mock(FirebaseToken.class);
        when(t.getUid()).thenReturn("uid-123");
        when(t.getEmail()).thenReturn(correo);
        when(t.getName()).thenReturn(nombre);
        when(t.getClaims()).thenReturn(emailVerified == null ? Map.of() : Map.of("email_verified", emailVerified));
        return t;
    }

    @Test
    void tokenConCorreoVerificadoDaLaIdentidad() {
        GoogleIdentidad g = FirebaseVerificadorGoogle.identidadDe(token("ana@gmail.com", true, "Ana Pérez"));

        assertEquals("uid-123", g.uid());
        assertEquals("ana@gmail.com", g.correo());
        assertEquals("Ana Pérez", g.nombre());
        assertTrue(g.correoVerificado());
    }

    @Test
    void correoNoVerificadoEs401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> FirebaseVerificadorGoogle.identidadDe(token("ana@gmail.com", false, "Ana")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("No pudimos verificar tu cuenta de Google", ex.getMessage());
    }

    @Test
    void sinLaClaimEmailVerifiedEs401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> FirebaseVerificadorGoogle.identidadDe(token("ana@gmail.com", null, "Ana")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void sinCorreoEs401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> FirebaseVerificadorGoogle.identidadDe(token(null, true, "Ana")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void tokenVacioEs400() {
        FirebaseVerificadorGoogle v = new FirebaseVerificadorGoogle("", "");

        ApiException ex = assertThrows(ApiException.class, () -> v.verificar("   "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Falta el token de Google", ex.getMessage());
    }

    @Test
    void sinConfiguracionDeFirebaseResponde503() {
        FirebaseVerificadorGoogle v = new FirebaseVerificadorGoogle("", "");

        ApiException ex = assertThrows(ApiException.class, () -> v.verificar("eyJhbGciOi..."));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }
}