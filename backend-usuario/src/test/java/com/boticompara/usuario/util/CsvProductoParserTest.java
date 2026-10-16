package com.boticompara.usuario.util;

import com.boticompara.usuario.dto.ErrorFila;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.util.CsvProductoParser.FilaProducto;
import com.boticompara.usuario.util.CsvProductoParser.Resultado;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvProductoParserTest {

    private static final String CAB = "nombreComercial,principioActivo,presentacion,precio,stock,stockMinimo";

    private static byte[] csv(String... lineas) {
        return String.join("\n", lineas).getBytes(StandardCharsets.UTF_8);
    }

    private static void assertBadRequest(byte[] datos) {
        ApiException ex = assertThrows(ApiException.class, () -> CsvProductoParser.parsear(datos));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void parseaFilasValidasConYSinStockMinimo() {
        Resultado r = CsvProductoParser.parsear(csv(CAB,
                "Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,5",
                "Ibuprofeno Genérico,Ibuprofeno,Tableta 400 mg x 10,3.2,100,"));

        assertEquals(2, r.filas().size());
        assertTrue(r.errores().isEmpty());
        FilaProducto a = r.filas().get(0);
        assertEquals(2, a.fila());
        assertEquals("Panadol", a.nombreComercial());
        assertEquals(new BigDecimal("8.50"), a.precio());
        assertEquals(40, a.stock());
        assertEquals(5, a.stockMinimo());
        FilaProducto b = r.filas().get(1);
        assertEquals(3, b.fila());
        assertEquals("Ibuprofeno Genérico", b.nombreComercial());
        assertEquals(new BigDecimal("3.20"), b.precio());
        assertNull(b.stockMinimo());
    }

    @Test
    void aceptaEncabezadoSinStockMinimo() {
        Resultado r = CsvProductoParser.parsear(csv(
                "nombreComercial,principioActivo,presentacion,precio,stock",
                "Panadol,Paracetamol,Tableta,8.5,40"));

        assertEquals(1, r.filas().size());
        assertNull(r.filas().get(0).stockMinimo());
    }

    @Test
    void aceptaColumnasEnCualquierOrdenYConMayusculas() {
        Resultado r = CsvProductoParser.parsear(csv(
                "STOCK,Precio,Presentacion,PrincipioActivo,NombreComercial",
                "40,8.5,Tableta,Paracetamol,Panadol"));

        assertTrue(r.errores().isEmpty());
        FilaProducto f = r.filas().get(0);
        assertEquals("Panadol", f.nombreComercial());
        assertEquals("Paracetamol", f.principioActivo());
        assertEquals(40, f.stock());
    }

    @Test
    void respetaComasDentroDeComillas() {
        Resultado r = CsvProductoParser.parsear(csv(CAB,
                "Panadol,Paracetamol,\"Tableta 500 mg, x 10\",8.50,40,"));

        assertEquals("Tableta 500 mg, x 10", r.filas().get(0).presentacion());
    }

    @Test
    void ignoraElBomDeExcel() {
        byte[] datos = ("\uFEFF" + CAB + "\nPanadol,Paracetamol,Tableta,8.50,40,").getBytes(StandardCharsets.UTF_8);

        Resultado r = CsvProductoParser.parsear(datos);

        assertEquals(1, r.filas().size());
    }

    @Test
    void aceptaSaltosDeLineaDeWindows() {
        byte[] datos = (CAB + "\r\nPanadol,Paracetamol,Tableta,8.50,40,\r\nAspirina,Acido acetilsalicilico,Tableta,1.20,10,\r\n")
                .getBytes(StandardCharsets.UTF_8);

        Resultado r = CsvProductoParser.parsear(datos);

        assertEquals(2, r.filas().size());
        assertEquals(3, r.filas().get(1).fila());
    }

    @Test
    void informaCadaFilaInvalidaConSuNumeroDeFila() {
        Resultado r = CsvProductoParser.parsear(csv(CAB,
                "A,P,Tableta,0,40,5",
                "B,P,Tableta,abc,10,",
                "C,P,Tableta,2.555,10,",
                "D,P,Tableta,2.50,-1,",
                "E,P,Tableta,5.00,10.5,",
                ",P,Tableta,1,1,",
                "G,P,Tableta,1,1,"));

        assertEquals(1, r.filas().size());
        assertEquals(8, r.filas().get(0).fila());
        assertEquals(List.of(
                new ErrorFila(2, "El precio debe ser mayor a 0."),
                new ErrorFila(3, "El precio debe ser un número (usa punto para los decimales)."),
                new ErrorFila(4, "El precio admite hasta 2 decimales."),
                new ErrorFila(5, "El stock no puede ser negativo."),
                new ErrorFila(6, "El stock debe ser un número entero."),
                new ErrorFila(7, "El nombre comercial es obligatorio.")), r.errores());
    }

    @Test
    void ignoraLineasEnBlancoPeroConservaLaNumeracion() {
        Resultado r = CsvProductoParser.parsear(csv(CAB,
                "",
                "Panadol,Paracetamol,Tableta,8.50,40,",
                ",,,,,",
                "Aspirina,Acido acetilsalicilico,Tableta,1.20,10,"));

        assertEquals(2, r.filas().size());
        assertEquals(3, r.filas().get(0).fila());
        assertEquals(5, r.filas().get(1).fila());
        assertTrue(r.errores().isEmpty());
    }

    @Test
    void detectaProductosDuplicadosEnElArchivo() {
        Resultado r = CsvProductoParser.parsear(csv(CAB,
                "Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,",
                " panadol ,Otro,TABLETA 500 MG X 10,9.00,10,"));

        assertEquals(1, r.filas().size());
        assertEquals(List.of(new ErrorFila(3, "Producto duplicado en el archivo (ya aparece en la fila 2).")),
                r.errores());
    }

    @Test
    void permiteMismoNombreConOtraPresentacion() {
        Resultado r = CsvProductoParser.parsear(csv(CAB,
                "Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,",
                "Panadol,Paracetamol,Jarabe 120 ml,12.00,10,"));

        assertEquals(2, r.filas().size());
        assertTrue(r.errores().isEmpty());
    }

    @Test
    void rechazaFilaConMasColumnasQueElEncabezado() {
        Resultado r = CsvProductoParser.parsear(csv(
                "nombreComercial,principioActivo,presentacion,precio,stock",
                "A,P,Tableta,1,1,extra",
                "B,P,Tableta,1,1,"));

        assertEquals(1, r.filas().size());
        assertEquals(3, r.filas().get(0).fila());
        assertEquals(List.of(new ErrorFila(2, "La fila tiene más columnas que el encabezado.")), r.errores());
    }

    @Test
    void rechazaArchivoVacio() {
        assertBadRequest(new byte[0]);
        assertBadRequest("\n\n  \n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rechazaArchivoSoloConEncabezado() {
        assertBadRequest(csv(CAB));
    }

    @Test
    void rechazaEncabezadoInvalido() {
        assertBadRequest(csv("nombre,precio,stock", "Panadol,8.50,40"));
    }

    @Test
    void rechazaPuntoYComaComoSeparador() {
        assertBadRequest(csv("nombreComercial;principioActivo;presentacion;precio;stock",
                "Panadol;Paracetamol;Tableta;8.50;40"));
    }

    @Test
    void rechazaArchivoQueNoEstaEnUtf8() {
        byte[] ansi = (CAB + "\nGenérico,Ibuprofeno,Tableta,1,1,").getBytes(StandardCharsets.ISO_8859_1);

        assertBadRequest(ansi);
    }

    @Test
    void rechazaArchivoBinario() {
        assertBadRequest(new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x01, 0x02});
    }

    @Test
    void rechazaComillasSinCerrar() {
        assertBadRequest(csv(CAB, "\"Panadol,Paracetamol,Tableta,8.50,40,"));
    }

    @Test
    void aceptaExactamenteMilFilas() {
        StringBuilder sb = new StringBuilder(CAB);
        for (int i = 1; i <= 1000; i++) {
            sb.append("\nProducto ").append(i).append(",Principio,Tableta,1.50,10,");
        }

        Resultado r = CsvProductoParser.parsear(sb.toString().getBytes(StandardCharsets.UTF_8));

        assertEquals(1000, r.filas().size());
        assertTrue(r.errores().isEmpty());
    }

    @Test
    void rechazaMasDeMilFilas() {
        StringBuilder sb = new StringBuilder(CAB);
        for (int i = 1; i <= 1001; i++) {
            sb.append("\nProducto ").append(i).append(",Principio,Tableta,1.50,10,");
        }

        assertBadRequest(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}