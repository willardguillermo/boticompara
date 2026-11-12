package com.boticompara.usuario.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H68: cliente de Gemini contra un servidor HTTP local de prueba (viene con el JDK).
 * Nunca llama a Gemini ni gasta cuota.
 */
class GeminiRestClienteTest {

    private static final String CLAVE = "clave-de-prueba-123";
    private static final Duration PLAZO = Duration.ofSeconds(5);
    private static final String TEXTO_OK = "{\"candidates\":[{\"content\":{\"role\":\"model\","
            + "\"parts\":[{\"text\":\"Hola\"}]},\"finishReason\":\"STOP\"}]}";
    private static final String CON_LLAMADA = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
            + "{\"functionCall\":{\"id\":\"call-1\",\"name\":\"buscar_precios\","
            + "\"args\":{\"texto\":\"paracetamol\",\"tipo\":\"principio\"}},\"thoughtSignature\":\"FIRMA-ABC\"}]}}]}";

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer servidor;
    private volatile int status = 200;
    private volatile String cuerpo = TEXTO_OK;
    private volatile long demoraMs;
    private volatile String claveRecibida;
    private volatile String uriRecibida;
    private volatile String cuerpoRecibido;

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1beta", ex -> {
            claveRecibida = ex.getRequestHeaders().getFirst("x-goog-api-key");
            uriRecibida = ex.getRequestURI().toString();
            cuerpoRecibido = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (demoraMs > 0) {
                try {
                    Thread.sleep(demoraMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, datos.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(datos);
            }
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    private GeminiRestCliente cliente(String clave) {
        return new GeminiRestCliente(json, clave, "modelo-prueba",
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta");
    }

    private static DeclaracionHerramienta herramienta() {
        return new DeclaracionHerramienta("buscar_precios", "Busca precios",
                Map.of("type", "OBJECT", "properties", Map.of("texto", Map.of("type", "STRING")),
                        "required", List.of("texto")));
    }

    private static GeminiPedido pedido(List<Turno> turnos, boolean permitir) {
        return new GeminiPedido("Eres el asistente", turnos, List.of(herramienta()), permitir);
    }

    private GeminiException falla(GeminiRestCliente c, Duration plazo) {
        return assertThrows(GeminiException.class,
                () -> c.generar(pedido(List.of(new Turno.Usuario("hola")), true), plazo));
    }

    @Test
    void armaElPedidoConInstruccionesHistorialYHerramientas() throws IOException {
        GeminiRespuesta r = cliente(CLAVE).generar(pedido(List.of(new Turno.Usuario("Hola"),
                new Turno.Asistente("¿Qué buscas?"), new Turno.Usuario("paracetamol")), true), PLAZO);

        assertEquals("Hola", r.texto());
        assertTrue(r.llamadas().isEmpty());
        assertEquals("/v1beta/models/modelo-prueba:generateContent", uriRecibida);
        JsonNode body = json.readTree(cuerpoRecibido);
        assertEquals("Eres el asistente", body.at("/systemInstruction/parts/0/text").asText());
        assertEquals(3, body.get("contents").size());
        assertEquals("user", body.at("/contents/0/role").asText());
        assertEquals("model", body.at("/contents/1/role").asText());
        assertEquals("¿Qué buscas?", body.at("/contents/1/parts/0/text").asText());
        assertEquals("paracetamol", body.at("/contents/2/parts/0/text").asText());
        JsonNode declaracion = body.at("/tools/0/functionDeclarations/0");
        assertEquals("buscar_precios", declaracion.get("name").asText());
        assertEquals("Busca precios", declaracion.get("description").asText());
        assertEquals("OBJECT", declaracion.at("/parameters/type").asText());
        assertEquals("texto", declaracion.at("/parameters/required/0").asText());
        assertEquals("AUTO", body.at("/toolConfig/functionCallingConfig/mode").asText());
    }

    @Test
    void laClaveViajaSoloEnElHeader() {
        cliente(CLAVE).generar(pedido(List.of(new Turno.Usuario("hola")), true), PLAZO);

        assertEquals(CLAVE, claveRecibida);
        assertFalse(uriRecibida.contains(CLAVE));
        assertFalse(cuerpoRecibido.contains(CLAVE));
    }

    @Test
    void sinHerramientas_noEnviaToolsNiToolConfig() throws IOException {
        cliente(CLAVE).generar(new GeminiPedido("x", List.of(new Turno.Usuario("hola")), List.of(), true), PLAZO);

        JsonNode body = json.readTree(cuerpoRecibido);
        assertFalse(body.has("tools"));
        assertFalse(body.has("toolConfig"));
    }

    @Test
    void conHerramientasDesactivadas_usaElModoNone() throws IOException {
        cliente(CLAVE).generar(pedido(List.of(new Turno.Usuario("hola")), false), PLAZO);

        JsonNode body = json.readTree(cuerpoRecibido);
        assertEquals("NONE", body.at("/toolConfig/functionCallingConfig/mode").asText());
        assertTrue(body.has("tools"));
    }

    @Test
    void leeLaLlamadaAUnaHerramienta() {
        cuerpo = CON_LLAMADA;

        GeminiRespuesta r = cliente(CLAVE).generar(pedido(List.of(new Turno.Usuario("paracetamol")), true), PLAZO);

        assertEquals("", r.texto());
        assertEquals(1, r.llamadas().size());
        LlamadaHerramienta llamada = r.llamadas().get(0);
        assertEquals("call-1", llamada.id());
        assertEquals("buscar_precios", llamada.nombre());
        assertEquals("paracetamol", llamada.args().get("texto").asText());
        assertEquals("FIRMA-ABC", r.contenido().at("/parts/0/thoughtSignature").asText());
    }

    @Test
    void reenviaElPasoDelModeloTalCualYLosResultadosComoDatos() throws IOException {
        cuerpo = CON_LLAMADA;
        GeminiRestCliente c = cliente(CLAVE);
        GeminiRespuesta primera = c.generar(pedido(List.of(new Turno.Usuario("paracetamol")), true), PLAZO);
        cuerpo = TEXTO_OK;

        c.generar(pedido(List.of(new Turno.Usuario("paracetamol"), new Turno.Modelo(primera.contenido()),
                new Turno.Resultados(List.of(
                        new Turno.Resultado("call-1", "buscar_precios", Map.of("total", 1)),
                        new Turno.Resultado(null, "buscar_alternativas", Map.of("error", "No encontrado"))))),
                true), PLAZO);

        JsonNode contents = json.readTree(cuerpoRecibido).get("contents");
        assertEquals(3, contents.size());
        assertEquals(primera.contenido(), contents.get(1));
        assertEquals("FIRMA-ABC", contents.at("/1/parts/0/thoughtSignature").asText());
        assertEquals("user", contents.at("/2/role").asText());
        JsonNode r1 = contents.at("/2/parts/0/functionResponse");
        assertEquals("call-1", r1.get("id").asText());
        assertEquals("buscar_precios", r1.get("name").asText());
        assertEquals(1, r1.at("/response/total").asInt());
        JsonNode r2 = contents.at("/2/parts/1/functionResponse");
        assertFalse(r2.has("id"));
        assertEquals("No encontrado", r2.at("/response/error").asText());
    }

    @Test
    void elTextoNoIncluyeElRazonamientoDelModelo() {
        cuerpo = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                + "{\"text\":\"pensando...\",\"thought\":true},{\"text\":\"Cuesta \"},{\"text\":\"2 soles\"}]}}]}";

        GeminiRespuesta r = cliente(CLAVE).generar(pedido(List.of(new Turno.Usuario("hola")), true), PLAZO);

        assertEquals("Cuesta 2 soles", r.texto());
    }

    @Test
    void llamadaSinId_quedaConIdNull() {
        cuerpo = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                + "{\"functionCall\":{\"name\":\"ver_stock_bajo\",\"args\":{}}}]}}]}";

        GeminiRespuesta r = cliente(CLAVE).generar(pedido(List.of(new Turno.Usuario("hola")), true), PLAZO);

        assertNull(r.llamadas().get(0).id());
        assertEquals("ver_stock_bajo", r.llamadas().get(0).nombre());
    }

    @Test
    void cuotaAgotada429_noDisponible() {
        status = 429;
        cuerpo = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"quota\"}}";

        GeminiException e = falla(cliente(CLAVE), PLAZO);

        assertFalse(e.esTiempoAgotado());
        // La cuota agotada no se arregla reintentando
        assertFalse(e.esReintentable());
        assertFalse(e.getMessage().contains(CLAVE));
    }

    @Test
    void error503O500_esUnFalloTemporalQueSePuedeReintentar() {
        for (int codigo : new int[]{503, 500}) {
            status = codigo;
            cuerpo = "no es json";

            GeminiException e = falla(cliente(CLAVE), PLAZO);

            assertTrue(e.esReintentable(), "HTTP " + codigo);
            assertFalse(e.esTiempoAgotado(), "HTTP " + codigo);
        }
    }

    @Test
    void claveRechazada403_noDisponible() {
        status = 403;
        cuerpo = "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}";

        GeminiException e = falla(cliente(CLAVE), PLAZO);

        assertFalse(e.esTiempoAgotado());
        assertFalse(e.esReintentable());
    }

    @Test
    void cuerpoIlegible_noDisponible() {
        cuerpo = "<html>error</html>";

        assertFalse(falla(cliente(CLAVE), PLAZO).esTiempoAgotado());
    }

    @Test
    void respuestaBloqueadaSinContenido_noDisponible() {
        cuerpo = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";

        assertFalse(falla(cliente(CLAVE), PLAZO).esTiempoAgotado());
    }

    @Test
    void siGeminiTardaMasQueElPlazo_tiempoAgotado() {
        demoraMs = 1500;

        GeminiException e = falla(cliente(CLAVE), Duration.ofMillis(200));

        assertTrue(e.esTiempoAgotado());
        assertFalse(e.esReintentable());
    }

    @Test
    void sinPlazo_tiempoAgotadoYNoLlama() {
        assertTrue(falla(cliente(CLAVE), Duration.ZERO).esTiempoAgotado());
        assertNull(uriRecibida);
    }

    @Test
    void sinClave_noDisponibleYNoLlama() {
        assertFalse(falla(cliente("  "), PLAZO).esTiempoAgotado());
        assertNull(uriRecibida);
    }

    @Test
    void servicioCaido_noDisponible() {
        // Puerto 1 de la propia máquina: nadie escucha ahí, la conexión se rechaza al instante
        GeminiRestCliente c = new GeminiRestCliente(json, CLAVE, "modelo-prueba", "http://127.0.0.1:1/v1beta");

        assertFalse(falla(c, PLAZO).esTiempoAgotado());
    }
}
