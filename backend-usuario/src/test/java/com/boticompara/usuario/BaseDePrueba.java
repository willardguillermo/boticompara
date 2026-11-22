package com.boticompara.usuario;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Para las pruebas que necesitan una base: se omiten, en lugar de fallar, en una PC sin DB_HOST. */
public final class BaseDePrueba {

    private BaseDePrueba() {
    }

    /**
     * Se llama desde el constructor de la prueba: así corre antes de que Spring arranque y pida la conexión, y cada
     * prueba sale como omitida. Desde un @BeforeAll no se contarían en el total.
     */
    public static void exigir() {
        assumeTrue(hayBase(), "se omite: sin base configurada");
    }

    /** Los mismos lugares de application.properties: la variable de entorno o el .env de la carpeta del backend. */
    private static boolean hayBase() {
        String host = System.getenv("DB_HOST");
        if (host == null) {
            host = delEnv();
        }
        return host != null && !host.isBlank();
    }

    private static String delEnv() {
        Path env = Path.of(".env");
        if (!Files.isRegularFile(env)) {
            return null;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(env)) {
            p.load(in);
        } catch (IOException e) {
            return null;
        }
        return p.getProperty("DB_HOST");
    }
}
