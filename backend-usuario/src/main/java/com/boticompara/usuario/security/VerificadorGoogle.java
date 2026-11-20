package com.boticompara.usuario.security;

/** Verifica el idToken de Firebase. Es una interfaz para poder probar el login sin Firebase real. */
public interface VerificadorGoogle {

    GoogleIdentidad verificar(String idToken);
}