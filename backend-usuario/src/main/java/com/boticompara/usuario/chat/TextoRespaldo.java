package com.boticompara.usuario.chat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * H68: texto de la respuesta de respaldo. Lo arma el servidor cuando las herramientas ya encontraron productos
 * pero Gemini no llegó a redactar la respuesta. Solo describe las tarjetas (cuántas hay, la más barata, su botica
 * y su distrito): nunca da consejos de salud. Sigue las reglas de respuesta: texto plano y precios como se dicen.
 */
final class TextoRespaldo {

    private static final int MAX_NOMBRE = 80;

    private TextoRespaldo() {
    }

    /** productos no puede estar vacío: sin productos no hay respaldo. */
    static String de(List<ChatProducto> productos) {
        ChatProducto barato = productos.stream()
                .min(Comparator.comparing(ChatProducto::precio))
                .orElseThrow();
        String descripcion = recortar(barato.nombreComercial()) + " a " + precio(barato.precio())
                + " en " + recortar(barato.boticaNombre()) + ", " + recortar(barato.boticaDistrito()) + ".";
        String texto = productos.size() == 1
                ? "Encontré 1 opción: " + descripcion
                : "Encontré " + productos.size() + " opciones. La más barata es " + descripcion;
        // Los nombres los escriben los dueños: se limpian igual que un texto del modelo
        return LimpiadorTexto.limpiar(texto);
    }

    /** 2.20 → "2 soles con 20"; 6.00 → "6 soles"; 1.50 → "1 sol con 50"; 0.50 → "50 céntimos". */
    static String precio(BigDecimal precio) {
        BigDecimal redondeado = precio.setScale(2, RoundingMode.HALF_UP);
        long soles = redondeado.longValue();
        int centimos = redondeado.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        if (soles == 0) {
            return centimos + (centimos == 1 ? " céntimo" : " céntimos");
        }
        String entero = soles + (soles == 1 ? " sol" : " soles");
        return centimos == 0 ? entero : entero + " con " + (centimos < 10 ? "0" + centimos : centimos);
    }

    private static String recortar(String texto) {
        String limpio = texto == null ? "" : texto.strip();
        return limpio.length() <= MAX_NOMBRE ? limpio : limpio.substring(0, MAX_NOMBRE);
    }
}
