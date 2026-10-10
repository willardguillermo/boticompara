package com.boticompara.usuario.service;

import com.boticompara.usuario.error.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class StorageService {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String claveSecreta;
    private final String bucket;

    public StorageService(@Value("${supabase.url:}") String url,
                          @Value("${supabase.secret-key:}") String claveSecreta,
                          @Value("${supabase.bucket-licencias:licencias}") String bucket) {
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.claveSecreta = claveSecreta;
        this.bucket = bucket;
    }

    /** Sube (o reemplaza) un archivo en el bucket. */
    public void subir(String ruta, byte[] contenido, String contentType) {
        verificarConfiguracion();
        HttpRequest request = base(ruta)
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(contenido))
                .build();
        HttpResponse<String> resp = enviar(request);
        if (resp.statusCode() / 100 != 2) {
            log.error("Supabase Storage rechazó la subida de {} (HTTP {}): {}", ruta, resp.statusCode(), resp.body());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo guardar el archivo. Intenta de nuevo");
        }
    }

    /** Mejor esfuerzo: si falla, solo queda un archivo huérfano y se avisa en el log. */
    public void eliminar(String ruta) {
        try {
            verificarConfiguracion();
            HttpResponse<String> resp = enviar(base(ruta).DELETE().build());
            if (resp.statusCode() / 100 != 2) {
                log.warn("No se pudo eliminar {} (HTTP {})", ruta, resp.statusCode());
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo eliminar {}: {}", ruta, e.getMessage());
        }
    }

    private HttpRequest.Builder base(String ruta) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + ruta))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", claveSecreta);
        // Las claves nuevas (sb_secret_...) van solo en apikey; las antiguas (JWT) también en Authorization
        if (claveSecreta.startsWith("eyJ")) {
            b.header("Authorization", "Bearer " + claveSecreta);
        }
        return b;
    }

    private HttpResponse<String> enviar(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.error("No se pudo conectar con Supabase Storage: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo guardar el archivo. Intenta de nuevo");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo guardar el archivo. Intenta de nuevo");
        }
    }

    private void verificarConfiguracion() {
        if (baseUrl.isBlank() || claveSecreta.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El almacenamiento de archivos no está configurado");
        }
    }
}