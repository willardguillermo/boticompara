package com.boticompara.usuario.chat;

import com.boticompara.usuario.chat.gemini.GeminiCliente;
import com.boticompara.usuario.chat.gemini.GeminiException;
import com.boticompara.usuario.chat.gemini.GeminiRespuesta;
import com.boticompara.usuario.chat.herramienta.RegistroHerramientas;
import com.boticompara.usuario.controller.BusquedaController;
import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.error.GlobalExceptionHandler;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.BusquedaService;
import com.boticompara.usuario.service.RegistroBusquedaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H68: POST /chat por HTTP, junto a GET /productos/buscar y con los dos manejadores de errores, para comprobar
 * que el chat no afecta a la búsqueda. Gemini y la base se simulan; no se levanta la aplicación completa.
 */
class ChatControllerTest {

    private final BusquedaService busqueda = mock(BusquedaService.class);
    private final RegistroBusquedaService registroBusquedas = mock(RegistroBusquedaService.class);
    private GeminiCliente gemini = (pedido, plazo) ->
            new GeminiRespuesta("Hola, ¿qué medicamento buscas?", List.of(), new ObjectMapper().createObjectNode());
    private MockMvc mvc;

    @BeforeEach
    void armar() {
        // El cliente de Gemini se consulta en cada pedido, así cada test puede cambiarlo
        ChatService service = new ChatService((pedido, plazo) -> gemini.generar(pedido, plazo),
                new RegistroHerramientas(List.of()), new LimitadorChat(2, 30), new InstruccionesSistema());
        // GlobalExceptionHandler va primero a propósito: ChatErrores debe ganar por su @Order
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(service),
                        new BusquedaController(busqueda, registroBusquedas))
                .setControllerAdvice(new GlobalExceptionHandler(), new ChatErrores())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(4L, "COMPRADOR"), null, List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ResultActions preguntar(String cuerpo) throws Exception {
        return mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    void respuesta200ConElFormatoDelContrato() throws Exception {
        preguntar("{\"mensaje\":\"Hola\",\"historial\":[{\"rol\":\"usuario\",\"texto\":\"Buenas\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value("Hola, ¿qué medicamento buscas?"))
                .andExpect(jsonPath("$.productos").isArray())
                .andExpect(jsonPath("$.productos").isEmpty());
    }

    @Test
    void mensajeVacio_400ConElFormatoDeErrorDeLaApi() throws Exception {
        preguntar("{\"mensaje\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.mensaje").value("Escribe tu pregunta"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void cuerpoQueNoEsJson_400() throws Exception {
        preguntar("{\"mensaje\":").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición no es un JSON válido"));
    }

    @Test
    void alPasarElLimite_429ConRetryAfterYElFormatoDeErrorDeLaApi() throws Exception {
        preguntar("{\"mensaje\":\"uno\"}").andExpect(status().isOk());
        preguntar("{\"mensaje\":\"dos\"}").andExpect(status().isOk());

        preguntar("{\"mensaje\":\"tres\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.mensaje")
                        .value("Hiciste muchas preguntas seguidas. Espera un minuto y vuelve a intentar"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void siGeminiFalla_elChatResponde503YLaBusquedaSigueFuncionando() throws Exception {
        gemini = (pedido, plazo) -> {
            throw GeminiException.noDisponible("cuota agotada");
        };
        when(busqueda.buscar("paracetamol", "principio", null, null, null, null)).thenReturn(List.of(
                new BusquedaResponse(9L, "Paracetamol Genérico", "Paracetamol", "Tableta 500 mg x 10",
                        new BigDecimal("2.20"), 90, 2L, "FarmaVida", "Jr. Trujillo 210", "Lurigancho-Chosica",
                        null, null, null)));

        preguntar("{\"mensaje\":\"paracetamol\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje")
                        .value("El asistente no está disponible en este momento. Intenta más tarde"));

        mvc.perform(get("/productos/buscar").param("q", "paracetamol").param("tipo", "principio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoId").value(9))
                .andExpect(jsonPath("$[0].precio").value(2.20));
        verify(registroBusquedas).registrar("paracetamol", "principio", 1, 4L);
    }

    @Test
    void siGeminiTardaDemasiado_504() throws Exception {
        gemini = (pedido, plazo) -> {
            throw GeminiException.tiempoAgotado("timeout");
        };

        preguntar("{\"mensaje\":\"hola\"}")
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.mensaje").value("El asistente tardó demasiado en responder. Intenta de nuevo"));
    }

    /** Aunque la búsqueda lanzara la excepción del chat, ChatErrores no la atiende: sigue en el manejador general. */
    @Test
    void chatErroresNoAplicaALaBusqueda() throws Exception {
        when(busqueda.buscar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new LimiteChatException("no debería salir", 30));

        mvc.perform(get("/productos/buscar").param("q", "paracetamol"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un error inesperado en el servidor"));
    }

    @Test
    void chatErroresSoloEstaAsignadoAChatController() {
        var anotacion = ChatErrores.class.getAnnotation(org.springframework.web.bind.annotation.RestControllerAdvice.class);

        assertArrayEquals(new Class<?>[]{ChatController.class}, anotacion.assignableTypes());
        assertEquals(1, ChatErrores.class.getDeclaredMethods().length);
    }
}
