package com.boticompara.usuario.chat;

import com.boticompara.usuario.chat.herramienta.Herramienta;

import java.util.ArrayList;
import java.util.List;

/**
 * H68: deja la respuesta del modelo como la pide el contrato: texto plano, sin markdown ni enlaces. El formato
 * depende del rol (docs/API.md, sección 8):
 * - Comprador: una sola línea de máximo 600 caracteres. La app la lee en voz alta (H71).
 * - Dueño: lo lee en la web, que respeta los saltos de línea. Máximo 900 caracteres (cabe en un texto del
 *   historial, de 1000) y, si pidió una lista, como máximo 8 líneas que empiezan con "- ".
 */
final class LimpiadorTexto {

    static final int MAX = 600;
    static final int MAX_DUENO = 900;
    static final int MAX_LINEAS_LISTA = 8;
    static final String VINETA = "- ";
    /** Al recortar se busca el final de una línea o de una oración, pero sin quedarse con menos de esto. */
    private static final int MINIMO_AL_RECORTAR = 200;
    /** Viñetas de markdown al inicio de una línea: -, *, •, + o una numeración "1." / "1)". */
    private static final String PATRON_VINETA = "^\\s*(?:[-*•+]|\\d{1,2}[.)])\\s+";

    private LimpiadorTexto() {
    }

    /** Formato del comprador: una sola línea. */
    static String limpiar(String texto) {
        return limpiar(texto, Herramienta.COMPRADOR);
    }

    static String limpiar(String texto, String rol) {
        if (texto == null) {
            return "";
        }
        String t = quitarMarkdown(texto);
        if (!Herramienta.DUENO_BOTICA.equals(rol)) {
            t = t.replaceAll("(?m)" + PATRON_VINETA, "")
                    .replaceAll("[*`]|~~|__", "")
                    .replaceAll("\\s+", " ")
                    .strip();
            return recortar(t, MAX);
        }
        return recortar(lineasDelDueno(t), MAX_DUENO);
    }

    /** Lo que se quita en los dos roles: bloques de código, enlaces (se queda su texto) y títulos. */
    private static String quitarMarkdown(String texto) {
        return texto
                .replaceAll("```[a-zA-Z]*", " ")
                // [texto](url) y ![texto](url) → texto
                .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?i)(?:https?://|www\\.)\\S+", " ")
                .replaceAll("(?m)^\\s*#{1,6}\\s+", "");
    }

    /**
     * Una línea por renglón, sin líneas vacías. Toda viñeta se escribe "- " y se quedan solo las 8 primeras; las
     * líneas que no son de la lista se conservan.
     */
    private static String lineasDelDueno(String texto) {
        List<String> lineas = new ArrayList<>();
        int enLista = 0;
        for (String linea : texto.split("\\R")) {
            boolean esVineta = linea.matches(PATRON_VINETA + ".*");
            String limpia = linea.replaceFirst(PATRON_VINETA, "")
                    .replaceAll("[*`]|~~|__", "")
                    .replaceAll("\\s+", " ")
                    .strip();
            if (limpia.isEmpty()) {
                continue;
            }
            if (esVineta) {
                if (++enLista > MAX_LINEAS_LISTA) {
                    continue;
                }
                limpia = VINETA + limpia;
            }
            lineas.add(limpia);
        }
        return String.join("\n", lineas);
    }

    private static String recortar(String t, int max) {
        if (t.length() <= max) {
            return t;
        }
        // Primero el último salto de línea (no deja una viñeta a medias); el comprador no tiene saltos
        int salto = t.lastIndexOf('\n', max);
        if (salto >= MINIMO_AL_RECORTAR) {
            return t.substring(0, salto);
        }
        // Último fin de oración (punto, ! o ? seguido de espacio) dentro del máximo
        for (int i = max - 1; i >= MINIMO_AL_RECORTAR; i--) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(t.charAt(i + 1))) {
                return t.substring(0, i + 1);
            }
        }
        int espacio = t.lastIndexOf(' ', max - 1);
        return t.substring(0, espacio > 0 ? espacio : max - 1).stripTrailing() + "…";
    }
}
