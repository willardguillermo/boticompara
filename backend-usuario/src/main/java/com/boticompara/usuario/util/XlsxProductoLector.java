package com.boticompara.usuario.util;

import com.boticompara.usuario.error.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee la primera hoja de un .xlsx (H11) y la convierte en filas de texto, para que
 * CsvProductoParser aplique exactamente las mismas validaciones que al CSV.
 */
final class XlsxProductoLector {

    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};                         // PK.. (.xlsx)
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};                               // .xls antiguo

    /** Excel trabaja con 15 cifras significativas: así 1.1*3 se lee como 3.3 y no como 3.3000000000000003. */
    private static final MathContext PRECISION_EXCEL = new MathContext(15);

    private XlsxProductoLector() {
    }

    static boolean esXlsx(byte[] datos) {
        return empieza(datos, ZIP);
    }

    static boolean esXls(byte[] datos) {
        return empieza(datos, OLE2);
    }

    /** Devuelve las filas no vacías de la primera hoja; el número de fila es el que muestra Excel. */
    static List<CsvProductoParser.Registro> leer(byte[] datos) {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(datos))) {
            if (libro.getNumberOfSheets() == 0) {
                throw ApiException.badRequest("El archivo está vacío.");
            }
            Sheet hoja = libro.getSheetAt(0);
            List<CsvProductoParser.Registro> registros = new ArrayList<>();
            for (Row fila : hoja) {
                List<String> campos = new ArrayList<>();
                for (int c = 0; c < fila.getLastCellNum(); c++) {
                    campos.add(texto(fila.getCell(c)));
                }
                // Igual que en el CSV: las filas vacías (o con formato pero sin datos) no cuentan
                if (campos.stream().anyMatch(s -> !s.isBlank())) {
                    registros.add(new CsvProductoParser.Registro(fila.getRowNum() + 1, campos));
                }
            }
            return registros;
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("No se pudo leer el archivo Excel. Revisa que sea un .xlsx válido.");
        }
    }

    private static String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        CellType tipo = celda.getCellType();
        if (tipo == CellType.FORMULA) {
            tipo = celda.getCachedFormulaResultType();
        }
        return switch (tipo) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(celda.getNumericCellValue())
                    .round(PRECISION_EXCEL).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(celda.getBooleanCellValue());
            default -> "";   // celdas en blanco o con error (#DIV/0!, #N/A...)
        };
    }

    private static boolean empieza(byte[] datos, byte[] firma) {
        if (datos == null || datos.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (datos[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}