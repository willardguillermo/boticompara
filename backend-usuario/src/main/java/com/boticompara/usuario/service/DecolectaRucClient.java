package com.boticompara.usuario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * H2: consulta el RUC en Decolecta (GET {url}?numero={ruc}).
 * 200 → datos; 404 o 422 → no existe; cualquier otra cosa (timeout, 401, 429, 5xx...) → no disponible.
 * Nunca escribe el token en el log.
 */
@Slf4j
@Component
public class DecolectaRucClient implements ConsultaRucClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper json;
    private final String url;
    private final String token;

    public DecolectaRucClient(ObjectMapper json,
                              @Value("${ruc.api.url:}") String url,
                              @Value("${ruc.api.token:}") String token) {
        this.json = json;
        this.url = url.trim();
        this.token = token.trim();
    }

    @Override
    public ResultadoSunat consultar(String ruc) {
        if (url.isEmpty() || token.isEmpty()) {
            log.warn("RUC_API_URL o RUC_API_TOKEN no están configurados: el RUC {} solo se valida localmente", ruc);
            return ResultadoSunat.noDisponible();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "?numero=" + ruc))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status == 404 || status == 422) {
                return ResultadoSunat.noExiste();
            }
            if (status != 200) {
                log.warn("Decolecta respondió HTTP {} al consultar el RUC {}", status, ruc);
                return ResultadoSunat.noDisponible();
            }
            JsonNode datos = json.readTree(resp.body());
            String razonSocial = texto(datos, "razon_social");
            String estado = texto(datos, "estado");
            String condicion = texto(datos, "condicion");
            if (razonSocial == null || estado == null || condicion == null) {
                log.warn("Decolecta devolvió una respuesta incompleta para el RUC {}", ruc);
                return ResultadoSunat.noDisponible();
            }
            return ResultadoSunat.encontrado(razonSocial, estado, condicion);
        } catch (IOException e) {
            // Incluye el timeout (HttpTimeoutException) y un JSON inválido
            log.warn("No se pudo consultar el RUC {} en Decolecta: {}", ruc, e.toString());
            return ResultadoSunat.noDisponible();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoSunat.noDisponible();
        } catch (RuntimeException e) {
            log.warn("Error inesperado al consultar el RUC {} en Decolecta: {}", ruc, e.toString());
            return ResultadoSunat.noDisponible();
        }
    }

    private static String texto(JsonNode datos, String campo) {
        JsonNode v = datos.get(campo);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            return null;
        }
        return v.asText().trim();
    }
}