package com.boticompara.usuario.auth;

/** SEC-05: demasiados intentos fallidos de login. ErroresLogin la convierte en un 429 con Retry-After. */
public class LimiteLoginException extends RuntimeException {

    private final long reintentarEnSegundos;

    public LimiteLoginException(String mensaje, long reintentarEnSegundos) {
        super(mensaje);
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    public long getReintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}