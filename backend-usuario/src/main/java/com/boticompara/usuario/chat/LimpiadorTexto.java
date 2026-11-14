package com.boticompara.usuario.chat;

/**
 * H68: deja la respuesta del modelo como la pide el contrato: texto plano, sin markdown ni enlaces, en una
 * sola línea y de máximo 600 caracteres. La app la lee en voz alta (H71).
 */
final class LimpiadorTexto {

    static final int MAX = 600;
    /** Al recortar se busca el final de una oración, pero sin quedarse con menos de esto. */
    private static final int MINIMO_AL_RECORTAR = 200;

    private LimpiadorTexto() {
    }

    static String limpiar(String texto) {
        if (texto == null) {
            return "";
        }
        String t = texto
                .replaceAll("```[a-zA-Z]*", " ")
                // [texto](url) y ![texto](url) → texto
                .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?i)(?:https?://|www\\.)\\S+", " ")
                // Títulos y viñetas al inicio de una línea
                .replaceAll("(?m)^\\s*#{1,6}\\s+", "")
                .replaceAll("(?m)^\\s*(?:[-*•+]|\\d{1,2}[.)])\\s+", "")
                .replaceAll("[*`]|~~|__", "")
                .replaceAll("\\s+", " ")
                .strip();
        return recortar(t);
    }

    private static String recortar(String t) {
        if (t.length() <= MAX) {
            return t;
        }
        // Último fin de oración (punto, ! o ? seguido de espacio) dentro del máximo
        for (int i = MAX - 1; i >= MINIMO_AL_RECORTAR; i--) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && t.charAt(i + 1) == ' ') {
                return t.substring(0, i + 1);
            }
        }
        int espacio = t.lastIndexOf(' ', MAX - 1);
        return t.substring(0, espacio > 0 ? espacio : MAX - 1).stripTrailing() + "…";
    }
}
