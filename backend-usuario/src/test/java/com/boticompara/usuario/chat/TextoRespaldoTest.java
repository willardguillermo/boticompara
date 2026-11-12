package com.boticompara.usuario.chat;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H68: el texto de respaldo solo describe las tarjetas y se puede leer en voz alta. */
class TextoRespaldoTest {

    private static ChatProducto producto(long id, String nombre, String precio, String botica) {
        return new ChatProducto(id, nombre, "Paracetamol", "Tableta 500 mg x 10", new BigDecimal(precio), 10, null,
                2L, botica, "Lurigancho-Chosica", null);
    }

    @Test
    void variosProductos_diceCuantosYCualEsElMasBarato() {
        String texto = TextoRespaldo.de(List.of(producto(3, "Paracetamol MK", "2.50", "Botica Salud Total"),
                producto(9, "Paracetamol Genérico", "2.20", "FarmaVida"), producto(5, "Panadol", "4.00", "FarmaVida")));

        assertEquals("Encontré 3 opciones. La más barata es Paracetamol Genérico a 2 soles con 20 en FarmaVida, "
                + "Lurigancho-Chosica.", texto);
    }

    @Test
    void unSoloProducto_loDiceEnSingular() {
        assertEquals("Encontré 1 opción: Amoxicilina a 6 soles en Botica Salud Total, Lurigancho-Chosica.",
                TextoRespaldo.de(List.of(producto(4, "Amoxicilina", "6.00", "Botica Salud Total"))));
    }

    @Test
    void losPreciosSeEscribenComoSeDicen() {
        assertEquals("2 soles con 20", TextoRespaldo.precio(new BigDecimal("2.20")));
        assertEquals("2 soles con 05", TextoRespaldo.precio(new BigDecimal("2.05")));
        assertEquals("6 soles", TextoRespaldo.precio(new BigDecimal("6.00")));
        assertEquals("1 sol", TextoRespaldo.precio(new BigDecimal("1")));
        assertEquals("1 sol con 50", TextoRespaldo.precio(new BigDecimal("1.5")));
        assertEquals("50 céntimos", TextoRespaldo.precio(new BigDecimal("0.50")));
        assertEquals("120 soles con 90", TextoRespaldo.precio(new BigDecimal("120.90")));
    }

    @Test
    void esTextoPlanoCortoYSinEnlacesAunqueElDuenoLosPongaEnElNombre() {
        String texto = TextoRespaldo.de(List.of(
                producto(1, "**Oferta** [clic](https://malo.example) " + "x".repeat(300), "2.20", "FarmaVida"),
                producto(2, "Otro", "3.00", "FarmaVida")));

        assertTrue(texto.length() <= 600);
        assertFalse(texto.contains("*") || texto.contains("http") || texto.contains("["));
        assertTrue(texto.startsWith("Encontré 2 opciones. La más barata es Oferta clic"));
    }

    @Test
    void soloDescribeProductos_sinConsejosDeSalud() {
        String texto = TextoRespaldo.de(List.of(producto(9, "Paracetamol Genérico", "2.20", "FarmaVida"),
                producto(3, "Paracetamol MK", "2.50", "Botica Salud Total"))).toLowerCase();

        for (String palabra : List.of("toma", "dosis", "recomiendo", "sirve para", "cada 8 horas", "mg cada")) {
            assertFalse(texto.contains(palabra), palabra);
        }
    }
}
