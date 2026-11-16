package com.boticompara.usuario.chat;

/**
 * H68: lo que el servidor sabe del pedido y el modelo no: quién pregunta (sale del JWT) y dónde está.
 * Solo lo reciben las herramientas; nunca viaja a Gemini.
 */
public record ContextoChat(Long usuarioId, String rol, Double lat, Double lng) {

    public boolean conUbicacion() {
        return lat != null && lng != null;
    }
}
