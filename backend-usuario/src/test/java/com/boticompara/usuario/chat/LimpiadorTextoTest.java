package com.boticompara.usuario.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }
}
