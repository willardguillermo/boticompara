package com.boticompara.usuario.chat.gemini;

import java.time.Duration;

/**
 * H68: única puerta de salida hacia Gemini. Solo recibe un GeminiPedido, que no tiene campos para el id del
 * usuario, sus datos ni su ubicación. En las pruebas se reemplaza por un cliente falso: no gastan cuota.
 */
public interface GeminiCliente {

    /**
     * Una llamada al modelo. plazo es el tiempo máximo de esta llamada.
     *
     * @throws GeminiException si Gemini no está disponible o no responde dentro del plazo
     */
    GeminiRespuesta generar(GeminiPedido pedido, Duration plazo);
}
