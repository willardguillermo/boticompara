package com.boticompara.usuario.limite;

/**
 * Se pasó un límite de uso (validar-ruc, registro, recuperar). GlobalExceptionHandler la convierte en un 429 con el
 * header Retry-After.
 */
public class LimiteExcedidoException extends RuntimeException {

    private final long reintentarEnSegundos;

    public LimiteExcedidoException(String mensaje, long reintentarEnSegundos) {
        super(mensaje);
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    public long getReintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}
