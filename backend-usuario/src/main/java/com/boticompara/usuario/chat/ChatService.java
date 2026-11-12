package com.boticompara.usuario.chat;

import com.boticompara.usuario.chat.gemini.DeclaracionHerramienta;
import com.boticompara.usuario.chat.gemini.GeminiCliente;
import com.boticompara.usuario.chat.gemini.GeminiException;
import com.boticompara.usuario.chat.gemini.GeminiPedido;
import com.boticompara.usuario.chat.gemini.GeminiRespuesta;
import com.boticompara.usuario.chat.gemini.LlamadaHerramienta;
import com.boticompara.usuario.chat.gemini.Turno;
import com.boticompara.usuario.chat.herramienta.RegistroHerramientas;
import com.boticompara.usuario.chat.herramienta.ResultadoHerramienta;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.security.UsuarioAutenticado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H68: POST /chat. Valida el pedido, cuenta la pregunta en el límite del usuario y conversa con Gemini:
 * como máximo 2 rondas de herramientas más la respuesta (3 llamadas), todo dentro de 20 segundos.
 * No guarda nada entre un pedido y otro, y no depende de UsuarioRepository: nunca carga nombre ni correo.
 * A Gemini solo le llega el GeminiPedido; el id del usuario y la ubicación van en el ContextoChat, que solo
 * reciben las herramientas.
 */
@Slf4j
@Service
public class ChatService {

    static final int MAX_MENSAJE = 500;
    static final int MAX_TEXTO_HISTORIAL = 1000;
    static final int MAX_HISTORIAL = 10;
    static final int MAX_RONDAS = 2;
    static final int MAX_HERRAMIENTAS_POR_RONDA = 3;
    static final int MAX_PRODUCTOS = 10;
    static final Duration PLAZO = Duration.ofSeconds(20);

    static final String NO_DISPONIBLE = "El asistente no está disponible en este momento. Intenta más tarde";
    static final String TARDO_DEMASIADO = "El asistente tardó demasiado en responder. Intenta de nuevo";

    private final GeminiCliente gemini;
    private final RegistroHerramientas herramientas;
    private final LimitadorChat limitador;
    private final InstruccionesSistema instrucciones;
    private final Clock reloj;

    @Autowired
    public ChatService(GeminiCliente gemini, RegistroHerramientas herramientas, LimitadorChat limitador,
                       InstruccionesSistema instrucciones) {
        this(gemini, herramientas, limitador, instrucciones, Clock.systemUTC());
    }

    ChatService(GeminiCliente gemini, RegistroHerramientas herramientas, LimitadorChat limitador,
                InstruccionesSistema instrucciones, Clock reloj) {
        this.gemini = gemini;
        this.herramientas = herramientas;
        this.limitador = limitador;
        this.instrucciones = instrucciones;
        this.reloj = reloj;
    }

    public ChatResponse responder(UsuarioAutenticado yo, ChatRequest pedido) {
        // 1. Validaciones (400): si fallan, la pregunta no cuenta en el límite
        String mensaje = validarMensaje(pedido.mensaje());
        List<Turno> turnos = historial(pedido.historial());
        validarUbicacion(pedido.lat(), pedido.lng());

        // 2. Límite por usuario (429). Desde aquí la pregunta cuenta, aunque termine en 503 o 504
        limitador.registrar(yo.id());

        ContextoChat contexto = new ContextoChat(yo.id(), yo.rol(), pedido.lat(), pedido.lng());
        turnos.add(new Turno.Usuario(mensaje));
        String sistema = instrucciones.para(yo.rol(), contexto.conUbicacion());
        List<DeclaracionHerramienta> declaraciones = herramientas.declaraciones(yo.rol());
        Instant limite = reloj.instant().plus(PLAZO);
        Map<Long, ChatProducto> productos = new LinkedHashMap<>();
        List<String> usadas = new ArrayList<>();
        try {
            for (int ronda = 0; ; ronda++) {
                // Después de la segunda ronda de herramientas, el modelo está obligado a responder con texto
                boolean permitirHerramientas = ronda < MAX_RONDAS;
                GeminiRespuesta respuesta = gemini.generar(
                        new GeminiPedido(sistema, List.copyOf(turnos), declaraciones, permitirHerramientas),
                        restante(limite));
                if (respuesta.llamadas().isEmpty() || !permitirHerramientas) {
                    ChatResponse resultado = finalizar(respuesta, productos);
                    // Para vigilar la cuota y los tiempos. Sin el id del usuario ni el texto de la pregunta
                    log.info("Chat {}: {} llamada(s) a Gemini, herramientas {}, {} tarjeta(s), {} ms", yo.rol(),
                            ronda + 1, usadas, resultado.productos().size(),
                            Duration.between(limite.minus(PLAZO), reloj.instant()).toMillis());
                    return resultado;
                }
                respuesta.llamadas().forEach(llamada -> usadas.add(llamada.nombre()));
                turnos.add(new Turno.Modelo(respuesta.contenido()));
                turnos.add(new Turno.Resultados(ejecutar(contexto, respuesta.llamadas(), productos)));
            }
        } catch (GeminiException e) {
            // La causa (clave, cuota, modelo) queda solo en el log del cliente de Gemini
            if (e.esTiempoAgotado()) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, TARDO_DEMASIADO);
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, NO_DISPONIBLE);
        }
    }

    /** Tiempo que queda de los 20 segundos: es el plazo de la siguiente llamada a Gemini. */
    private Duration restante(Instant limite) {
        Duration queda = Duration.between(reloj.instant(), limite);
        if (queda.isZero() || queda.isNegative()) {
            throw GeminiException.tiempoAgotado("Se acabaron los 20 segundos del pedido");
        }
        return queda;
    }

    /** Ejecuta las herramientas de una ronda (máximo 3) y junta sus tarjetas, sin repetidos. */
    private List<Turno.Resultado> ejecutar(ContextoChat contexto, List<LlamadaHerramienta> llamadas,
                                           Map<Long, ChatProducto> productos) {
        List<Turno.Resultado> resultados = new ArrayList<>();
        for (int i = 0; i < llamadas.size(); i++) {
            LlamadaHerramienta llamada = llamadas.get(i);
            ResultadoHerramienta resultado = i < MAX_HERRAMIENTAS_POR_RONDA
                    ? herramientas.ejecutar(contexto, llamada.nombre(), llamada.args())
                    : ResultadoHerramienta.error("Demasiadas herramientas a la vez: máximo "
                            + MAX_HERRAMIENTAS_POR_RONDA + " por ronda");
            for (ChatProducto tarjeta : resultado.tarjetas()) {
                productos.putIfAbsent(tarjeta.productoId(), tarjeta);
            }
            resultados.add(new Turno.Resultado(llamada.id(), llamada.nombre(), resultado.paraModelo()));
        }
        return resultados;
    }

    private static ChatResponse finalizar(GeminiRespuesta respuesta, Map<Long, ChatProducto> productos) {
        String texto = LimpiadorTexto.limpiar(respuesta.texto());
        if (texto.isEmpty()) {
            log.warn("Gemini terminó sin texto ({} herramientas pendientes)", respuesta.llamadas().size());
            throw GeminiException.noDisponible("Respuesta sin texto");
        }
        return new ChatResponse(texto, productos.values().stream().limit(MAX_PRODUCTOS).toList());
    }

    private static String validarMensaje(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            throw ApiException.badRequest("Escribe tu pregunta");
        }
        String limpio = mensaje.strip();
        if (limpio.length() > MAX_MENSAJE) {
            throw ApiException.badRequest("La pregunta no puede pasar de 500 caracteres");
        }
        return limpio;
    }

    /**
     * Valida todo el historial y se queda con los 10 últimos mensajes. Lo envía el cliente, así que solo trae
     * texto: los pasos de herramientas de preguntas anteriores no viajan.
     */
    private static List<Turno> historial(List<MensajeHistorial> historial) {
        List<Turno> turnos = new ArrayList<>();
        if (historial == null) {
            return turnos;
        }
        for (MensajeHistorial m : historial) {
            if (m == null || m.texto() == null || m.texto().isBlank() || m.texto().length() > MAX_TEXTO_HISTORIAL) {
                throw ApiException.badRequest("El historial no es válido");
            }
            if ("usuario".equals(m.rol())) {
                turnos.add(new Turno.Usuario(m.texto().strip()));
            } else if ("asistente".equals(m.rol())) {
                turnos.add(new Turno.Asistente(m.texto().strip()));
            } else {
                throw ApiException.badRequest("El historial no es válido");
            }
        }
        List<Turno> ultimos = new ArrayList<>(turnos.subList(Math.max(0, turnos.size() - MAX_HISTORIAL), turnos.size()));
        // La conversación que recibe Gemini debe empezar con un mensaje del usuario
        while (!ultimos.isEmpty() && ultimos.get(0) instanceof Turno.Asistente) {
            ultimos.remove(0);
        }
        return ultimos;
    }

    /** Mismos mensajes que GET /productos/buscar (BusquedaService), como pide el contrato. */
    private static void validarUbicacion(Double lat, Double lng) {
        if (lat == null && lng == null) {
            return;
        }
        if (lat == null || lng == null) {
            throw ApiException.badRequest("Debes enviar lat y lng juntas");
        }
        if (!(lat >= -90 && lat <= 90)) {
            throw ApiException.badRequest("La latitud debe estar entre -90 y 90");
        }
        if (!(lng >= -180 && lng <= 180)) {
            throw ApiException.badRequest("La longitud debe estar entre -180 y 180");
        }
    }
}
