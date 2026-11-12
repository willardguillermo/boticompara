package com.boticompara.usuario.chat.gemini;

/** H68: Gemini no respondió. El mensaje es solo para el log: nunca se muestra al usuario ni lleva la clave. */
public class GeminiException extends RuntimeException {

    private final boolean tiempoAgotado;

    private GeminiException(String mensaje, boolean tiempoAgotado) {
        super(mensaje);
        this.tiempoAgotado = tiempoAgotado;
    }

    public static GeminiException noDisponible(String mensaje) {
        return new GeminiException(mensaje, false);
    }

    public static GeminiException tiempoAgotado(String mensaje) {
        return new GeminiException(mensaje, true);
    }

    /** true = se acabó el plazo (504); false = no disponible (503). */
    public boolean esTiempoAgotado() {
        return tiempoAgotado;
    }
}
