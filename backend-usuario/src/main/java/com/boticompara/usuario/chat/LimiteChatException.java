package com.boticompara.usuario.chat;

/** H68: el usuario pasó su límite de preguntas. ChatErrores la convierte en un 429 con el header Retry-After. */
public class LimiteChatException extends RuntimeException {

    private final long reintentarEnSegundos;

    public LimiteChatException(String mensaje, long reintentarEnSegundos) {
        super(mensaje);
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    public long getReintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}
