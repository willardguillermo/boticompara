package com.boticompara.usuario.chat;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * H68: red de seguridad que no depende de Gemini. Si el mensaje trae una frase clara de autolesión o de
 * emergencia física, el servidor responde al instante con un texto fijo y los números de ayuda. También define
 * la frase con esos números que cierra todos los mensajes de error del chat (429, 503 y 504).
 * La lista es corta y literal a propósito: un falso positivo solo muestra números de ayuda, pero no debe
 * activarse con frases comunes como "me duele la cabeza". Lo que no esté aquí lo responde Gemini.
 */
final class FiltroCrisis {

    static final String NUMEROS_DE_AYUDA = "Si es una emergencia, llama al SAMU 106. "
            + "Si piensas en hacerte daño, llama a la Línea 113, opción 5.";

    static final String TEXTO_AUTOLESION = "Lamento mucho que te sientas así y quiero que sepas que no estás solo. "
            + "Llama al SAMU al 106 o a la Línea 113, opción 5, que es la de salud mental del Ministerio de Salud. "
            + "Hay personas capacitadas dispuestas a escucharte y ayudarte en este momento.";

    static final String TEXTO_EMERGENCIA = "Llama al SAMU al 106 de inmediato. BotiCompara solo compara precios de "
            + "medicamentos y no puede atender una emergencia: ahora lo más importante es tu salud.";

    /** Las frases van ya normalizadas: en minúsculas y sin tildes ("daño" queda como "dano"). */
    static final List<String> AUTOLESION = List.of("hacerme dano", "matarme", "suicid", "quitarme la vida",
            "no quiero vivir", "quiero morir", "cortarme");
    static final List<String> EMERGENCIA = List.of("no puedo respirar", "dolor de pecho", "me duele el pecho",
            "intoxicacion", "envenenamiento", "convulsion", "desmayo");

    enum Grupo {
        AUTOLESION(TEXTO_AUTOLESION), EMERGENCIA(TEXTO_EMERGENCIA);

        private final String texto;

        Grupo(String texto) {
            this.texto = texto;
        }

        String texto() {
            return texto;
        }
    }

    private FiltroCrisis() {
    }

    /** Grupo que activa el mensaje, si activa alguno. Si tiene frases de los dos, gana la autolesión. */
    static Optional<Grupo> detectar(String mensaje) {
        String texto = normalizar(mensaje);
        if (AUTOLESION.stream().anyMatch(texto::contains)) {
            return Optional.of(Grupo.AUTOLESION);
        }
        if (EMERGENCIA.stream().anyMatch(texto::contains)) {
            return Optional.of(Grupo.EMERGENCIA);
        }
        return Optional.empty();
    }

    /** Agrega los números de ayuda al final de un mensaje de error del chat. */
    static String conNumerosDeAyuda(String mensaje) {
        return (mensaje.endsWith(".") ? mensaje : mensaje + ".") + " " + NUMEROS_DE_AYUDA;
    }

    /** Minúsculas, sin tildes ni diéresis, y con un solo espacio entre palabras. */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
