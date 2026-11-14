package com.boticompara.usuario.chat.gemini;

/** H68: Gemini no respondió. El mensaje es solo para el log: nunca se muestra al usuario ni lleva la clave. */
public class GeminiException extends RuntimeException {

    private final boolean tiempoAgotado;
    private final boolean reintentable;

    private GeminiException(String mensaje, boolean tiempoAgotado, boolean reintentable) {
        super(mensaje);
        this.tiempoAgotado = tiempoAgotado;
        this.reintentable = reintentable;
    }

    public static GeminiException noDisponible(String mensaje) {
        return new GeminiException(mensaje, false, false);
    }

    /** Google respondió 500 o 503: es un fallo pasajero de su lado y vale la pena un reintento. */
    public static GeminiException falloTemporal(String mensaje) {
        return new GeminiException(mensaje, false, true);
    }

    public static GeminiException tiempoAgotado(String mensaje) {
        return new GeminiException(mensaje, true, false);
    }

    /** true = Google respondió 500 o 503. Otros errores (cuota, clave, formato) no se reintentan. */
    public boolean esReintentable() {
        return reintentable;
    }

    /** true = se acabó el plazo (504); false = no disponible (503). */
    public boolean esTiempoAgotado() {
        return tiempoAgotado;
    }
}
