package com.boticompara.usuario.service;

import com.boticompara.usuario.service.ConsultaRucClient.ResultadoSunat;
import com.boticompara.usuario.service.ConsultaRucClient.Tipo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * H2: cliente de Decolecta contra un servidor HTTP local de prueba (viene con el JDK).
 * Nunca llama al servicio real ni gasta cuota.
 */
class DecolectaRucClientTest {

    private static final String RUC = "20131312955";

    private HttpServer servidor;
    private volatile int status;
    private volatile String cuerpo;
    private volatile String authRecibido;
    private volatile String queryRecibida;

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/sunat/ruc", ex -> {
            authRecibido = ex.getRequestHeaders().getFirst("Authorization");
            queryRecibida = ex.getRequestURI().getQuery();
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

    private DecolectaRucClient cliente(String token) {
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/sunat/ruc";
        return new DecolectaRucClient(new ObjectMapper(), url, token);
    }

    private void responder(int codigo, String json) {
        status = codigo;
        cuerpo = json;
    }

    @Test
    void respuesta200_devuelveLosDatosYEnviaTokenYNumero() {
        responder(200, "{\"razon_social\":\"SUNAT\",\"numero_documento\":\"20131312955\","
                + "\"estado\":\"ACTIVO\",\"condicion\":\"HABIDO\",\"direccion\":\"AV. GARCILASO\","
                + "\"locales_anexos\":[{\"distrito\":\"CALLAO\"}]}");

        ResultadoSunat r = cliente("token-prueba").consultar(RUC);

        assertEquals(Tipo.ENCONTRADO, r.tipo());
        assertEquals("SUNAT", r.razonSocial());
        assertEquals("ACTIVO", r.estado());
        assertEquals("HABIDO", r.condicion());
        assertEquals("Bearer token-prueba", authRecibido);
        assertEquals("numero=" + RUC, queryRecibida);
    }

    @Test
    void respuesta404_esRucQueNoExiste() {
        responder(404, "{\"message\":\"not found\"}");

        assertEquals(Tipo.NO_EXISTE, cliente("token-prueba").consultar(RUC).tipo());
    }

    @Test
    void respuesta422_esRucQueNoExiste() {
        responder(422, "{\"message\":\"invalid\"}");

        assertEquals(Tipo.NO_EXISTE, cliente("token-prueba").consultar(RUC).tipo());
    }

    @Test
    void respuesta401_noDisponible() {
        responder(401, "{\"message\":\"unauthorized\"}");

        assertEquals(Tipo.NO_DISPONIBLE, cliente("token-prueba").consultar(RUC).tipo());
    }

    @Test
    void respuesta500_noDisponible() {
        responder(500, "{}");

        assertEquals(Tipo.NO_DISPONIBLE, cliente("token-prueba").consultar(RUC).tipo());
    }

    @Test
    void respuestaIncompleta_noDisponible() {
        responder(200, "{\"razon_social\":\"SUNAT\",\"estado\":\"ACTIVO\"}");

        assertEquals(Tipo.NO_DISPONIBLE, cliente("token-prueba").consultar(RUC).tipo());
    }

    @Test
    void sinToken_noDisponibleYNoLlamaAlServicio() {
        responder(200, "{}");

        assertEquals(Tipo.NO_DISPONIBLE, cliente("").consultar(RUC).tipo());
        assertNull(queryRecibida);
    }

    @Test
    void servicioCaido_noDisponible() {
        // Puerto 1 de la propia máquina: nadie escucha ahí, la conexión se rechaza al instante
        DecolectaRucClient c = new DecolectaRucClient(new ObjectMapper(), "http://127.0.0.1:1/v1/sunat/ruc", "token-prueba");

        assertEquals(Tipo.NO_DISPONIBLE, c.consultar(RUC).tipo());
    }
}
