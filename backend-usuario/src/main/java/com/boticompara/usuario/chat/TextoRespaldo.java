package com.boticompara.usuario.chat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * H68: texto de la respuesta de respaldo. Lo arma el servidor cuando las herramientas ya encontraron productos
 * pero Gemini no llegó a redactar la respuesta. Solo describe las tarjetas: nunca da consejos de salud. Sigue
 * las reglas de respuesta: texto plano y precios como se dicen. El texto depende de dónde salieron las tarjetas:
 * del stock bajo del dueño, de su catálogo, o de una búsqueda entre boticas (ahí se nombra la más barata).
 */
final class TextoRespaldo {

    static final String VER_STOCK_BAJO = "ver_stock_bajo";
    static final String BUSCAR_EN_MI_CATALOGO = "buscar_en_mi_catalogo";
    private static final Set<String> DEL_CATALOGO_PROPIO = Set.of(VER_STOCK_BAJO, BUSCAR_EN_MI_CATALOGO);
    private static final int MAX_NOMBRE = 80;

    private TextoRespaldo() {
    }

    /**
     * productos no puede estar vacío: sin productos no hay respaldo. herramientas son las que devolvieron esas
     * tarjetas. Si todas salieron del catálogo del dueño no se compara precios: se dice cuántas hay.
     */
    static String de(List<ChatProducto> productos, Set<String> herramientas) {
        int n = productos.size();
        if (herramientas.equals(Set.of(VER_STOCK_BAJO))) {
            return n == 1
                    ? "Tienes 1 producto con stock bajo. Lo ves en la tarjeta."
                    : "Tienes " + n + " productos con stock bajo. Los ves en las tarjetas.";
        }
        if (!herramientas.isEmpty() && DEL_CATALOGO_PROPIO.containsAll(herramientas)) {
            return n == 1
                    ? "Encontré 1 producto en tu catálogo. Lo ves en la tarjeta."
                    : "Encontré " + n + " productos en tu catálogo. Los ves en las tarjetas.";
        }
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
