package com.boticompara.usuario.chat;

import com.boticompara.usuario.chat.herramienta.Herramienta;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * H68: instrucciones del sistema, un archivo de texto por rol en resources/chat/. Lo único que cambia entre
 * un pedido y otro es si hay ubicación (sí o no): las coordenadas nunca entran al texto.
 */
@Component
public class InstruccionesSistema {

    static final String MARCA_UBICACION = "{UBICACION}";
    static final String CON_UBICACION = "El usuario compartió su ubicación: puedes usar cerca=true en "
            + "buscar_precios y mencionar la distancia en kilómetros que devuelve.";
    static final String SIN_UBICACION = "El usuario no compartió su ubicación: no uses cerca=true ni ofrezcas "
            + "ordenar por cercanía.";

    private final String comprador = leer("chat/instrucciones-comprador.txt");
    private final String dueno = leer("chat/instrucciones-dueno.txt");

    public String para(String rol, boolean conUbicacion) {
        String base = Herramienta.DUENO_BOTICA.equals(rol) ? dueno : comprador;
        return base.replace(MARCA_UBICACION, conUbicacion ? CON_UBICACION : SIN_UBICACION);
    }

    private static String leer(String ruta) {
        try (InputStream in = new ClassPathResource(ruta).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta, e);
        }
    }
}
