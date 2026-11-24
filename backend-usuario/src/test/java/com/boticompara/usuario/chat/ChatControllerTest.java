package com.boticompara.usuario.chat;

import com.boticompara.usuario.chat.gemini.GeminiCliente;
import com.boticompara.usuario.chat.gemini.GeminiException;
import com.boticompara.usuario.chat.gemini.GeminiRespuesta;
import com.boticompara.usuario.chat.herramienta.RegistroHerramientas;
import com.boticompara.usuario.controller.BusquedaController;
import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
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
import java.util.Optional;

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

    /** Todos los mensajes de error del chat terminan con los números de ayuda. */
    private static final String NUMEROS = "Si es una emergencia, llama al SAMU 106. "
            + "Si piensas en hacerte daño, llama a la Línea 113, opción 5.";

    private final BusquedaService busqueda = mock(BusquedaService.class);
    private final RegistroBusquedaService registroBusquedas = mock(RegistroBusquedaService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final BoticaRepository boticas = mock(BoticaRepository.class);
    private GeminiCliente gemini = (pedido, plazo) ->
            new GeminiRespuesta("Hola, ¿qué medicamento buscas?", List.of(), new ObjectMapper().createObjectNode());
    private MockMvc mvc;

    @BeforeEach
    void armar() {
        // Por defecto todos pueden usar el asistente: comprador verificado y botica aprobada
        when(usuarios.findById(any())).thenReturn(Optional.of(usuario(true)));
        when(boticas.findByUsuarioId(any())).thenReturn(Optional.of(botica("APROBADO")));
        // El cliente de Gemini se consulta en cada pedido, así cada test puede cambiarlo
        ChatService service = new ChatService((pedido, plazo) -> gemini.generar(pedido, plazo),
                new RegistroHerramientas(List.of()), new LimitadorChat(2, 30, 3), new InstruccionesSistema());
        // GlobalExceptionHandler va primero a propósito: ChatErrores debe ganar por su @Order
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(new AccesoChat(usuarios, boticas), service),
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

    private static Usuario usuario(boolean verificado) {
        Usuario u = new Usuario();
        u.setCorreoVerificado(verificado);
        return u;
    }

    private static Botica botica(String estado) {
        Botica b = new Botica();
        b.setEstado(estado);
        return b;
    }

    private void comoDueno() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(1L, "DUENO_BOTICA"), null, List.of()));
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
                .andExpect(jsonPath("$.productos").isEmpty())
                .andExpect(jsonPath("$.respaldo").value(false))
                .andExpect(jsonPath("$.ayuda").value(false));
    }

    // ---------- Quién puede usar el asistente (403) ----------

    @Test
    void compradorSinCorreoVerificado_403SinLlamarAGemini() throws Exception {
        when(usuarios.findById(4L)).thenReturn(Optional.of(usuario(false)));
        gemini = (pedido, plazo) -> {
            throw new AssertionError("Con 403 no se llama a Gemini");
        };

        preguntar("{\"mensaje\":\"Hola\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.mensaje").value("Verifica tu correo para usar el asistente"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void compradorQueNoExiste_403() throws Exception {
        when(usuarios.findById(4L)).thenReturn(Optional.empty());

        preguntar("{\"mensaje\":\"Hola\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Verifica tu correo para usar el asistente"));
    }

    @Test
    void duenoConBoticaPendienteORechazada_403() throws Exception {
        comoDueno();
        for (String estado : List.of("PENDIENTE", "RECHAZADO")) {
            when(boticas.findByUsuarioId(1L)).thenReturn(Optional.of(botica(estado)));

            preguntar("{\"mensaje\":\"Hola\"}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value("El asistente se activa cuando tu botica sea aprobada"));
        }
    }

    @Test
    void duenoSinBotica_403() throws Exception {
        comoDueno();
        when(boticas.findByUsuarioId(1L)).thenReturn(Optional.empty());

        preguntar("{\"mensaje\":\"Hola\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("El asistente se activa cuando tu botica sea aprobada"));
    }

    @Test
    void duenoConBoticaAprobada_noNecesitaRevisarSuCorreo() throws Exception {
        comoDueno();
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(false)));

        preguntar("{\"mensaje\":\"Hola\"}").andExpect(status().isOk());
    }

    @Test
    void el403VaAntesDeValidarElCuerpo() throws Exception {
        when(usuarios.findById(4L)).thenReturn(Optional.of(usuario(false)));

        preguntar("{\"mensaje\":\"  \"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Verifica tu correo para usar el asistente"));
    }

    @Test
    void los403NoCuentanEnElLimite() throws Exception {
        // El límite de este test es 2 por minuto: tres 403 seguidos no lo gastan
        when(usuarios.findById(4L)).thenReturn(Optional.of(usuario(false)));
        for (int i = 0; i < 3; i++) {
            preguntar("{\"mensaje\":\"Hola\"}").andExpect(status().isForbidden());
        }
        when(usuarios.findById(4L)).thenReturn(Optional.of(usuario(true)));

        preguntar("{\"mensaje\":\"uno\"}").andExpect(status().isOk());
        preguntar("{\"mensaje\":\"dos\"}").andExpect(status().isOk());
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
                        .value("Hiciste muchas preguntas seguidas. Espera un minuto y vuelve a intentar. " + NUMEROS))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void alLlegarAlTopeGlobal_503ConSuMensajeYSinLlamarAGemini() throws Exception {
        java.util.concurrent.atomic.AtomicInteger llamadas = new java.util.concurrent.atomic.AtomicInteger();
        GeminiCliente normal = gemini;
        gemini = (pedido, plazo) -> {
            llamadas.incrementAndGet();
            return normal.generar(pedido, plazo);
        };
        // El tope global de este test es 3 y cada usuario puede hacer 2 por minuto: preguntan dos usuarios
        preguntar("{\"mensaje\":\"uno\"}").andExpect(status().isOk());
        preguntar("{\"mensaje\":\"dos\"}").andExpect(status().isOk());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(1L, "DUENO_BOTICA"), null, List.of()));
        preguntar("{\"mensaje\":\"tres\"}").andExpect(status().isOk());

        preguntar("{\"mensaje\":\"cuatro\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensaje").value("El asistente alcanzó su límite de hoy. Intenta mañana. " + NUMEROS));
        assertEquals(3, llamadas.get());
    }

    @Test
    void siGeminiFalla_elChatResponde503YLaBusquedaSigueFuncionando() throws Exception {
        gemini = (pedido, plazo) -> {
            throw GeminiException.noDisponible("cuota agotada");
        };
        when(busqueda.buscar("paracetamol", "principio", null, null, null, null)).thenReturn(List.of(
                new BusquedaResponse(9L, "Paracetamol Genérico", "Paracetamol", "Tableta 500 mg x 10",
                        new BigDecimal("2.20"), 90, 2L, "FarmaVida", "Jr. Trujillo 210", "Lurigancho-Chosica",
                        null, null, null, null)));

        preguntar("{\"mensaje\":\"paracetamol\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje")
                        .value("El asistente no está disponible en este momento. Intenta más tarde. " + NUMEROS));

        mvc.perform(get("/productos/buscar").param("q", "paracetamol").param("tipo", "principio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoId").value(9))
                .andExpect(jsonPath("$[0].precio").value(2.20));
        verify(registroBusquedas).registrar("paracetamol", "principio", 1, 4L);
    }

    @Test
    void unaFraseDeRiesgo_200ConAyudaSinLlamarAGemini() throws Exception {
        gemini = (pedido, plazo) -> {
            throw new AssertionError("El filtro de crisis no debe llamar a Gemini");
        };

        preguntar("{\"mensaje\":\"Últimamente pienso en hacerme daño\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value(FiltroCrisis.TEXTO_AUTOLESION))
                .andExpect(jsonPath("$.productos").isEmpty())
                .andExpect(jsonPath("$.respaldo").value(false))
                .andExpect(jsonPath("$.ayuda").value(true));
    }

    @Test
    void siGeminiTardaDemasiado_504() throws Exception {
        gemini = (pedido, plazo) -> {
            throw GeminiException.tiempoAgotado("timeout");
        };

        preguntar("{\"mensaje\":\"hola\"}")
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.mensaje").value("El asistente tardó demasiado en responder. Intenta de nuevo. " + NUMEROS));
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
