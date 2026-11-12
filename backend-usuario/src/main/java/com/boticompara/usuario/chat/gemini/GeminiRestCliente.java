package com.boticompara.usuario.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * H68: llama a generateContent de Gemini por REST (POST {base}/models/{modelo}:generateContent), con el
 * HttpClient del JDK, igual que DecolectaRucClient. La clave viaja solo en el header x-goog-api-key: nunca en
 * la URL, en el cuerpo ni en el log. Sin GEMINI_API_KEY la API arranca igual y el chat responde 503.
 */
@Slf4j
@Component
public class GeminiRestCliente implements GeminiCliente {

    static final String URL_BASE = "https://generativelanguage.googleapis.com/v1beta";
    static final String MODELO_POR_DEFECTO = "gemini-3.5-flash-lite";
    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXION).build();
    private final ObjectMapper json;
    private final String clave;
    private final String modelo;
    private final String urlBase;

    @Autowired
    public GeminiRestCliente(ObjectMapper json,
                             @Value("${gemini.api-key:}") String clave,
                             @Value("${gemini.model:}") String modelo) {
        this(json, clave, modelo, URL_BASE);
    }

    GeminiRestCliente(ObjectMapper json, String clave, String modelo, String urlBase) {
        this.json = json;
        this.clave = clave.trim();
        this.modelo = modelo.isBlank() ? MODELO_POR_DEFECTO : modelo.trim();
        this.urlBase = urlBase;
    }

    @Override
    public GeminiRespuesta generar(GeminiPedido pedido, Duration plazo) {
        if (clave.isEmpty()) {
            log.warn("GEMINI_API_KEY no está configurada: el asistente no puede responder");
            throw GeminiException.noDisponible("Falta GEMINI_API_KEY");
        }
        if (plazo.isZero() || plazo.isNegative()) {
            throw GeminiException.tiempoAgotado("No queda tiempo para llamar a Gemini");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(urlBase + "/models/" + modelo + ":generateContent"))
                    .timeout(plazo)
                    .header("x-goog-api-key", clave)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo(pedido)),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("Gemini respondió HTTP {} ({})", resp.statusCode(), estadoDelError(resp.body()));
                throw GeminiException.noDisponible("Gemini respondió HTTP " + resp.statusCode());
            }
            return respuesta(json.readTree(resp.body()));
        } catch (HttpConnectTimeoutException e) {
            log.warn("No se pudo conectar con Gemini: {}", e.toString());
            throw GeminiException.noDisponible("Sin conexión con Gemini");
        } catch (HttpTimeoutException e) {
            log.warn("Gemini no respondió en {} ms", plazo.toMillis());
            throw GeminiException.tiempoAgotado("Gemini no respondió a tiempo");
        } catch (IOException e) {
            // Incluye una respuesta que no es JSON
            log.warn("No se pudo consultar a Gemini: {}", e.toString());
            throw GeminiException.noDisponible("Error de red o respuesta ilegible de Gemini");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GeminiException.noDisponible("Llamada a Gemini interrumpida");
        } catch (IllegalArgumentException e) {
            // Un GEMINI_MODEL con caracteres que no caben en una URL
            log.warn("GEMINI_MODEL no es válido: {}", e.getMessage());
            throw GeminiException.noDisponible("GEMINI_MODEL no es válido");
        }
    }

    private ObjectNode cuerpo(GeminiPedido pedido) {
        ObjectNode raiz = json.createObjectNode();
        raiz.putObject("systemInstruction").putArray("parts").addObject().put("text", pedido.instrucciones());
        ArrayNode contents = raiz.putArray("contents");
        for (Turno turno : pedido.turnos()) {
            if (turno instanceof Turno.Usuario u) {
                texto(contents, "user", u.texto());
            } else if (turno instanceof Turno.Asistente a) {
                texto(contents, "model", a.texto());
            } else if (turno instanceof Turno.Modelo m) {
                // Tal como llegó: incluye la firma del razonamiento (thoughtSignature) y el id de cada llamada
                contents.add(m.contenido());
            } else if (turno instanceof Turno.Resultados r) {
                ObjectNode contenido = contents.addObject().put("role", "user");
                ArrayNode partes = contenido.putArray("parts");
                for (Turno.Resultado resultado : r.resultados()) {
                    ObjectNode fr = partes.addObject().putObject("functionResponse");
                    if (resultado.id() != null) {
                        fr.put("id", resultado.id());
                    }
                    fr.put("name", resultado.nombre());
                    fr.set("response", json.valueToTree(resultado.respuesta()));
                }
            }
        }
        if (!pedido.herramientas().isEmpty()) {
            ArrayNode declaraciones = raiz.putArray("tools").addObject().putArray("functionDeclarations");
            for (DeclaracionHerramienta h : pedido.herramientas()) {
                ObjectNode d = declaraciones.addObject().put("name", h.nombre()).put("description", h.descripcion());
                if (h.parametros() != null) {
                    d.set("parameters", json.valueToTree(h.parametros()));
                }
            }
            raiz.putObject("toolConfig").putObject("functionCallingConfig")
                    .put("mode", pedido.permitirHerramientas() ? "AUTO" : "NONE");
        }
        return raiz;
    }

    private static void texto(ArrayNode contents, String rol, String texto) {
        contents.addObject().put("role", rol).putArray("parts").addObject().put("text", texto);
    }

    private static GeminiRespuesta respuesta(JsonNode raiz) {
        JsonNode candidato = raiz.path("candidates").path(0);
        JsonNode contenido = candidato.path("content");
        if (!contenido.isObject()) {
            // Sin contenido: respuesta bloqueada o vacía. El motivo va solo al log
            log.warn("Gemini no devolvió contenido (finishReason={}, blockReason={})",
                    candidato.path("finishReason").asText("-"),
                    raiz.path("promptFeedback").path("blockReason").asText("-"));
            throw GeminiException.noDisponible("Gemini no devolvió contenido");
        }
        StringBuilder texto = new StringBuilder();
        List<LlamadaHerramienta> llamadas = new ArrayList<>();
        for (JsonNode parte : contenido.path("parts")) {
            JsonNode llamada = parte.get("functionCall");
            if (llamada != null) {
                JsonNode id = llamada.get("id");
                llamadas.add(new LlamadaHerramienta(id == null || id.isNull() ? null : id.asText(),
                        llamada.path("name").asText(""), llamada.path("args")));
            } else if (!parte.path("thought").asBoolean(false) && parte.hasNonNull("text")) {
                texto.append(parte.get("text").asText());
            }
        }
        return new GeminiRespuesta(texto.toString(), List.copyOf(llamadas), contenido);
    }

    /** Solo el código de estado del error (por ejemplo RESOURCE_EXHAUSTED), para el log. */
    private String estadoDelError(String cuerpo) {
        try {
            return json.readTree(cuerpo).path("error").path("status").asText("-");
        } catch (IOException | RuntimeException e) {
            return "-";
        }
    }
}
