package com.boticompara.usuario.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H68: la respuesta sale en texto plano, sin enlaces y de máximo 600 caracteres. */
class LimpiadorTextoTest {

    @Test
    void textoPlanoQuedaIgual() {
        String texto = "El más barato es el Paracetamol Genérico de FarmaVida, a 2 soles con 20.";

        assertEquals(texto, LimpiadorTexto.limpiar(texto));
    }

    @Test
    void quitaNegritasTitulosYVinetas() {
        String texto = "## Resultados\n\n- **Paracetamol MK** a 2 soles con 50\n* `Panadol` a 4 soles\n1. Otro";

        assertEquals("Resultados Paracetamol MK a 2 soles con 50 Panadol a 4 soles Otro", LimpiadorTexto.limpiar(texto));
    }

    @Test
    void quitaEnlacesYDejaSuTexto() {
        assertEquals("Mira FarmaVida o entra a ahora.",
                LimpiadorTexto.limpiar("Mira [FarmaVida](https://malo.example/x) o entra a https://malo.example ahora."));
    }

    @Test
    void conservaLosPreciosYLosNumerosDeEmergencia() {
        String texto = "Llama al SAMU al 106. También puedes llamar a la Línea 113, opción 5. Cuesta 2.50 soles.";

        assertEquals(texto, LimpiadorTexto.limpiar(texto));
    }

    @Test
    void juntaLosSaltosDeLineaEnUnaSolaLinea() {
        assertEquals("Hola. ¿Qué buscas?", LimpiadorTexto.limpiar("  Hola.\n\n¿Qué   buscas?  "));
    }

    @Test
    void textoLargo_seCortaEnElUltimoFinDeOracionDentroDe600() {
        String oracion = "Esta oración tiene exactamente cincuenta caracteres. ";
        String texto = oracion.repeat(15).strip();

        String r = LimpiadorTexto.limpiar(texto);

        assertTrue(r.length() <= 600);
        assertTrue(r.endsWith("caracteres."));
        assertEquals(oracion.repeat(11).strip(), r);
    }

    @Test
    void textoLargoSinPuntos_seCortaEnUnEspacioConPuntosSuspensivos() {
        String r = LimpiadorTexto.limpiar("palabra ".repeat(100));

        assertTrue(r.length() <= 600);
        assertTrue(r.endsWith("palabra…"));
    }

    @Test
    void nullOSoloMarkdown_quedaVacio() {
        assertEquals("", LimpiadorTexto.limpiar(null));
        assertEquals("", LimpiadorTexto.limpiar(" ** \n ``` "));
        assertEquals("", LimpiadorTexto.limpiar(null, DUENO));
        assertEquals("", LimpiadorTexto.limpiar(" ** \n ``` ", DUENO));
    }

    // ---------- Dueño: la web respeta los saltos de línea ----------

    private static final String DUENO = "DUENO_BOTICA";

    @Test
    void dueno_conservaLaListaYEscribeTodaViñetaComoGuion() {
        String texto = "Te sugiero:\n\n* **Pañuelos** desechables\n• Termómetros\n1. Alcohol   en gel\n2) `Pañales`\n+ Curitas";

        assertEquals("Te sugiero:\n- Pañuelos desechables\n- Termómetros\n- Alcohol en gel\n- Pañales\n- Curitas",
                LimpiadorTexto.limpiar(texto, DUENO));
    }

    @Test
    void dueno_quitaElRestoDelMarkdownYLosEnlaces() {
        String texto = "## Consejos\nMira [DIGEMID](https://malo.example/x) y **cotiza** con ~~uno~~ __varios__. "
                + "https://malo.example\n```java\n- ![foto](http://x/y.png) Vitrina";

        String r = LimpiadorTexto.limpiar(texto, DUENO);

        assertEquals("Consejos\nMira DIGEMID y cotiza con uno varios.\n- foto Vitrina", r);
        assertFalse(r.contains("*") || r.contains("#") || r.contains("http") || r.contains("[") || r.contains("`"));
    }

    @Test
    void dueno_conservaComoMaximo8LineasDeLista() {
        StringBuilder texto = new StringBuilder("Productos para invierno:\n");
        for (int i = 1; i <= 11; i++) {
            texto.append("- Producto ").append(i).append('\n');
        }
        texto.append("Cotiza con varios proveedores.");

        String r = LimpiadorTexto.limpiar(texto.toString(), DUENO);

        List<String> lineas = r.lines().toList();
        assertEquals(8, lineas.stream().filter(l -> l.startsWith("- ")).count());
        assertEquals("- Producto 8", lineas.get(8));
        assertFalse(r.contains("Producto 9"));
        assertEquals("Cotiza con varios proveedores.", lineas.get(lineas.size() - 1));
    }

    @Test
    void dueno_textoLargo_seCortaAl900EnUnSaltoDeLineaSinDejarUnaLineaAMedias() {
        List<String> originales = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(i -> "Consejo " + i + " " + "x".repeat(90)).toList();

        String r = LimpiadorTexto.limpiar(String.join("\n", originales), DUENO);

        assertTrue(r.length() <= LimpiadorTexto.MAX_DUENO, String.valueOf(r.length()));
        assertTrue(r.length() > LimpiadorTexto.MAX, "el dueño tiene más espacio que el comprador");
        assertTrue(originales.containsAll(r.lines().toList()));
    }

    @Test
    void comprador_sigueEnUnaSolaLineaAunqueTraigaUnaLista() {
        String texto = "Opciones:\n- Paracetamol MK\n- Panadol";

        assertEquals("Opciones: Paracetamol MK Panadol", LimpiadorTexto.limpiar(texto, "COMPRADOR"));
        assertEquals("Opciones: Paracetamol MK Panadol", LimpiadorTexto.limpiar(texto));
    }
}
