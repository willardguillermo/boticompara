package com.boticompara.usuario.util;

import com.boticompara.usuario.dto.ErrorFila;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.util.CsvProductoParser.FilaProducto;
import com.boticompara.usuario.util.CsvProductoParser.Resultado;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H11: carga masiva desde .xlsx. Los archivos se crean en memoria con POI. */
class XlsxProductoLectorTest {

    private static final Object[] CAB =
            {"nombreComercial", "principioActivo", "presentacion", "precio", "stock", "stockMinimo"};

    /**
     * Crea un .xlsx en memoria. Cada fila es un arreglo de valores: Number = celda numérica,
     * String que empieza con "=" = fórmula, otro String = texto, null = celda vacía.
     * Una fila null deja esa fila vacía en el Excel.
     */
    private static byte[] xlsx(Object[]... filas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Productos");
            for (int i = 0; i < filas.length; i++) {
                if (filas[i] == null) {
                    continue;
                }
                Row fila = hoja.createRow(i);
                for (int c = 0; c < filas[i].length; c++) {
                    Object v = filas[i][c];
                    if (v instanceof Number n) {
                        fila.createCell(c).setCellValue(n.doubleValue());
                    } else if (v instanceof String s && s.startsWith("=")) {
                        fila.createCell(c).setCellFormula(s.substring(1));
                    } else if (v != null) {
                        fila.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            // Igual que Excel al guardar: deja el resultado de las fórmulas calculado dentro del archivo
            libro.getCreationHelper().createFormulaEvaluator().evaluateAll();
            libro.write(out);
            return out.toByteArray();
        }
    }

    private static ApiException badRequest(byte[] datos) {
        ApiException ex = assertThrows(ApiException.class, () -> CsvProductoParser.parsearArchivo(datos));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        return ex;
    }

    @Test
    void xlsx_conCeldasNumericasYDeTexto_leeLasFilas() throws IOException {
        Resultado r = CsvProductoParser.parsearArchivo(xlsx(CAB,
                new Object[]{"Panadol", "Paracetamol", "Tableta 500 mg x 10", 8.5, 40, 5},
                new Object[]{"Ibuprofeno Genérico", "Ibuprofeno", "Tableta 400 mg x 10", "3.20", "100", null}));

        assertTrue(r.errores().isEmpty());
        assertEquals(2, r.filas().size());
        FilaProducto a = r.filas().get(0);
        assertEquals(2, a.fila());
        assertEquals(new BigDecimal("8.50"), a.precio());
        assertEquals(40, a.stock());
        assertEquals(5, a.stockMinimo());
        FilaProducto b = r.filas().get(1);
        assertEquals("Ibuprofeno Genérico", b.nombreComercial());
        assertEquals(new BigDecimal("3.20"), b.precio());
        assertEquals(100, b.stock());
        assertNull(b.stockMinimo());
    }

    @Test
    void xlsx_filaVaciaEnMedio_seIgnoraYElErrorUsaElNumeroDeFilaDeExcel() throws IOException {
        Resultado r = CsvProductoParser.parsearArchivo(xlsx(CAB,
                new Object[]{"Panadol", "Paracetamol", "Tableta 500 mg x 10", 8.5, 40, 5},
                null,
                new Object[]{"Aspirina", "Ácido acetilsalicílico", "Tableta 100 mg x 30", 0, 10, null}));

        assertEquals(1, r.filas().size());
        assertEquals(List.of(new ErrorFila(4, "El precio debe ser mayor a 0.")), r.errores());
    }

    @Test
    void xlsx_filaConCeldasEnBlanco_noCuentaComoFila() throws IOException {
        Resultado r = CsvProductoParser.parsearArchivo(xlsx(CAB,
                new Object[]{"Panadol", "Paracetamol", "Tableta 500 mg x 10", 8.5, 40, 5},
                new Object[]{"", "   ", null, "", null, null}));

        assertEquals(1, r.filas().size());
        assertTrue(r.errores().isEmpty());
    }

    @Test
    void xlsx_formula_usaElValorCalculadoConPrecisionDeExcel() throws IOException {
        Resultado r = CsvProductoParser.parsearArchivo(xlsx(CAB,
                new Object[]{"Panadol", "Paracetamol", "Tableta 500 mg x 10", "=1.1*3", "=20+20", null}));

        assertTrue(r.errores().isEmpty());
        assertEquals(new BigDecimal("3.30"), r.filas().get(0).precio());
        assertEquals(40, r.filas().get(0).stock());
    }

    @Test
    void xlsx_precioConTresDecimales_esErrorDeFila() throws IOException {
        Resultado r = CsvProductoParser.parsearArchivo(xlsx(CAB,
                new Object[]{"Panadol", "Paracetamol", "Tableta 500 mg x 10", 8.555, 40, 5}));

        assertTrue(r.filas().isEmpty());
        assertEquals(List.of(new ErrorFila(2, "El precio admite hasta 2 decimales.")), r.errores());
    }

    @Test
    void xlsx_stockConDecimales_esErrorDeFila() throws IOException {
        Resultado r = CsvProductoParser.parsearArchivo(xlsx(CAB,
                new Object[]{"Panadol", "Paracetamol", "Tableta 500 mg x 10", 8.5, 40.5, 5}));

        assertEquals(List.of(new ErrorFila(2, "El stock debe ser un número entero.")), r.errores());
    }

    @Test
    void xlsx_productoDuplicado_esErrorDeFila() throws IOException {
        Resultado r = CsvProductoParser.parsearArchivo(xlsx(CAB,
                new Object[]{"Panadol", "Paracetamol", "Tableta 500 mg x 10", 8.5, 40, 5},
                new Object[]{"PANADOL", "Paracetamol", "tableta 500 mg x 10", 9, 10, null}));

        assertEquals(1, r.filas().size());
        assertEquals(List.of(new ErrorFila(3, "Producto duplicado en el archivo (ya aparece en la fila 2).")),
                r.errores());
    }

    @Test
    void xlsx_encabezadoInvalido_responde400() throws IOException {
        ApiException ex = badRequest(xlsx(
                new Object[]{"nombre", "precio"},
                new Object[]{"Panadol", 8.5}));

        assertTrue(ex.getMessage().startsWith("Encabezado inválido."));
    }

    @Test
    void xlsx_soloEncabezado_responde400() throws IOException {
        ApiException ex = badRequest(xlsx(CAB));

        assertEquals("El archivo no tiene filas de productos.", ex.getMessage());
    }

    @Test
    void xlsx_hojaVacia_responde400() throws IOException {
        ApiException ex = badRequest(xlsx());

        assertEquals("El archivo está vacío.", ex.getMessage());
    }

    @Test
    void xlsAntiguo_responde400ConIndicacion() {
        byte[] xls = new byte[512];
        byte[] firma = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(firma, 0, xls, 0, firma.length);

        ApiException ex = badRequest(xls);

        assertEquals("Los archivos .xls (Excel 97-2003) no se admiten. Guárdalo como .xlsx o como CSV UTF-8.",
                ex.getMessage());
    }

    @Test
    void zipQueNoEsExcel_responde400() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("hola.txt"));
            zip.write("hola".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ApiException ex = badRequest(out.toByteArray());

        assertEquals("No se pudo leer el archivo Excel. Revisa que sea un .xlsx válido.", ex.getMessage());
    }

    @Test
    void csv_porParsearArchivo_sigueFuncionando() {
        byte[] csv = ("nombreComercial,principioActivo,presentacion,precio,stock,stockMinimo\n"
                + "Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,5\n").getBytes(StandardCharsets.UTF_8);

        Resultado r = CsvProductoParser.parsearArchivo(csv);

        assertEquals(1, r.filas().size());
        assertTrue(r.errores().isEmpty());
    }
}