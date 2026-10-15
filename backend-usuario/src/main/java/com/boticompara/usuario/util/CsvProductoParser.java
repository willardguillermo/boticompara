package com.boticompara.usuario.util;

import com.boticompara.usuario.dto.ErrorFila;
import com.boticompara.usuario.error.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lee y valida el CSV de carga masiva de productos (H11). No toca la base de datos. */
public final class CsvProductoParser {

    public static final int MAX_FILAS = 1000;

    private static final List<String> OBLIGATORIAS =
            List.of("nombrecomercial", "principioactivo", "presentacion", "precio", "stock");
    private static final String COLUMNAS =
            "nombreComercial,principioActivo,presentacion,precio,stock[,stockMinimo]";

    public record FilaProducto(int fila, String nombreComercial, String principioActivo, String presentacion,
                               BigDecimal precio, int stock, Integer stockMinimo) {

        /** Mismo producto = mismo nombre y presentación, sin distinguir mayúsculas ni espacios de más. */
        public String clave() {
            return clave(nombreComercial, presentacion);
        }

        public static String clave(String nombreComercial, String presentacion) {
            return nombreComercial.trim().toLowerCase(Locale.ROOT) + "|" + presentacion.trim().toLowerCase(Locale.ROOT);
        }
    }

    public record Resultado(List<FilaProducto> filas, List<ErrorFila> errores) {
    }

    private record Registro(int linea, List<String> campos) {
    }

    private static final class FilaInvalida extends RuntimeException {
        FilaInvalida(String mensaje) {
            super(mensaje.endsWith(".") ? mensaje : mensaje + ".", null, false, false);
        }
    }

    private CsvProductoParser() {
    }

    /**
     * Problemas del archivo completo (vacío, encabezado inválido, más de 1000 filas...) lanzan ApiException 400.
     * Los problemas de una fila se devuelven en la lista de errores y no frenan a las demás.
     */
    public static Resultado parsear(byte[] datos) {
        if (datos == null || datos.length == 0) {
            throw ApiException.badRequest("El archivo está vacío.");
        }
        String texto = decodificar(datos);
        if (texto.indexOf('\0') >= 0) {
            throw ApiException.badRequest("El archivo no es un CSV válido.");
        }
        if (texto.startsWith("\uFEFF")) {
            texto = texto.substring(1); // BOM que agrega Excel al guardar como CSV UTF-8
        }
        List<Registro> registros = leer(texto + "\n");
        if (registros.isEmpty()) {
            throw ApiException.badRequest("El archivo está vacío.");
        }

        Registro cabecera = registros.get(0);
        Map<String, Integer> indice = indiceDeColumnas(cabecera);
        List<Registro> filasDatos = registros.subList(1, registros.size());
        if (filasDatos.isEmpty()) {
            throw ApiException.badRequest("El archivo no tiene filas de productos.");
        }
        if (filasDatos.size() > MAX_FILAS) {
            throw ApiException.badRequest("El archivo supera el máximo de " + MAX_FILAS + " filas.");
        }

        List<FilaProducto> filas = new ArrayList<>();
        List<ErrorFila> errores = new ArrayList<>();
        Map<String, Integer> vistos = new HashMap<>();
        for (Registro r : filasDatos) {
            try {
                FilaProducto f = validar(r, indice, cabecera.campos().size());
                Integer previa = vistos.get(f.clave());
                if (previa != null) {
                    errores.add(new ErrorFila(r.linea(),
                            "Producto duplicado en el archivo (ya aparece en la fila " + previa + ")."));
                    continue;
                }
                vistos.put(f.clave(), r.linea());
                filas.add(f);
            } catch (FilaInvalida e) {
                errores.add(new ErrorFila(r.linea(), e.getMessage()));
            }
        }
        return new Resultado(filas, errores);
    }

    private static String decodificar(byte[] datos) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(datos))
                    .toString();
        } catch (CharacterCodingException e) {
            throw ApiException.badRequest(
                    "El archivo debe estar codificado en UTF-8. Guárdalo como \"CSV UTF-8\" desde Excel.");
        }
    }

    private static Map<String, Integer> indiceDeColumnas(Registro cabecera) {
        Map<String, Integer> indice = new HashMap<>();
        for (int i = 0; i < cabecera.campos().size(); i++) {
            indice.putIfAbsent(cabecera.campos().get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        if (!OBLIGATORIAS.stream().allMatch(indice::containsKey)) {
            throw ApiException.badRequest(
                    "Encabezado inválido. Se esperan las columnas " + COLUMNAS + " separadas por coma.");
        }
        return indice;
    }

    /** Lector CSV mínimo: comas, comillas dobles, comillas escapadas ("") y saltos de línea CRLF o LF. */
    private static List<Registro> leer(String texto) {
        List<Registro> registros = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        int linea = 1;
        int lineaInicio = 1;
        int n = texto.length();

        for (int i = 0; i < n; i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < n && texto.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    if (c == '\n') {
                        linea++;
                    }
                    campo.append(c);
                }
            } else if (c == '"' && campo.length() == 0) {
                entreComillas = true;
            } else if (c == ',') {
                campos.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                campos.add(campo.toString());
                campo.setLength(0);
                // Las líneas vacías (o solo con comas, como las que deja Excel) no cuentan como fila
                if (campos.stream().anyMatch(s -> !s.isBlank())) {
                    registros.add(new Registro(lineaInicio, new ArrayList<>(campos)));
                }
                campos.clear();
                linea++;
                lineaInicio = linea;
            } else {
                campo.append(c);
            }
        }
        if (entreComillas) {
            throw ApiException.badRequest("El archivo tiene comillas sin cerrar.");
        }
        return registros;
    }

    private static FilaProducto validar(Registro r, Map<String, Integer> indice, int columnas) {
        for (int k = columnas; k < r.campos().size(); k++) {
            if (!r.campos().get(k).isBlank()) {
                throw new FilaInvalida("La fila tiene más columnas que el encabezado");
            }
        }
        String nombre = celda(r, indice, "nombrecomercial");
        String principio = celda(r, indice, "principioactivo");
        String presentacion = celda(r, indice, "presentacion");
        texto(nombre, "El nombre comercial es obligatorio", "El nombre comercial admite máximo 150 caracteres", 150);
        texto(principio, "El principio activo es obligatorio", "El principio activo admite máximo 150 caracteres", 150);
        texto(presentacion, "La presentación es obligatoria", "La presentación admite máximo 100 caracteres", 100);

        BigDecimal precio = precio(celda(r, indice, "precio"));
        int stock = entero(celda(r, indice, "stock"), "El stock");
        Integer stockMinimo = null;
        String sm = celda(r, indice, "stockminimo");
        if (!sm.isEmpty()) {
            stockMinimo = entero(sm, "El stock mínimo");
        }
        return new FilaProducto(r.linea(), nombre, principio, presentacion, precio, stock, stockMinimo);
    }

    private static String celda(Registro r, Map<String, Integer> indice, String columna) {
        Integer i = indice.get(columna);
        if (i == null || i >= r.campos().size()) {
            return "";
        }
        return r.campos().get(i).trim();
    }

    private static void texto(String valor, String vacio, String largo, int max) {
        if (valor.isEmpty()) {
            throw new FilaInvalida(vacio);
        }
        if (valor.length() > max) {
            throw new FilaInvalida(largo);
        }
    }

    private static BigDecimal precio(String texto) {
        if (texto.isEmpty()) {
            throw new FilaInvalida("El precio es obligatorio");
        }
        BigDecimal p;
        try {
            p = new BigDecimal(texto);
        } catch (NumberFormatException e) {
            throw new FilaInvalida("El precio debe ser un número (usa punto para los decimales)");
        }
        if (p.signum() <= 0) {
            throw new FilaInvalida("El precio debe ser mayor a 0");
        }
        if (p.stripTrailingZeros().scale() > 2) {
            throw new FilaInvalida("El precio admite hasta 2 decimales");
        }
        if (p.precision() - p.scale() > 8) {
            throw new FilaInvalida("El precio es demasiado alto");
        }
        return p.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static int entero(String texto, String nombre) {
        if (texto.isEmpty()) {
            throw new FilaInvalida(nombre + " es obligatorio");
        }
        int valor;
        try {
            valor = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            throw new FilaInvalida(nombre + " debe ser un número entero");
        }
        if (valor < 0) {
            throw new FilaInvalida(nombre + " no puede ser negativo");
        }
        return valor;
    }
}