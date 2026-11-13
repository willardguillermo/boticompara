package com.boticompara.usuario.chat.herramienta;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * H68: lo que el modelo ve de un producto. Solo datos del producto y de la botica: sin coordenadas, dirección,
 * RUC ni datos de personas. productoId va para que pueda pedir buscar_alternativas. Los textos se recortan
 * porque los escriben los dueños.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductoParaModelo(Long productoId, String nombre, String principioActivo, String presentacion,
                                 BigDecimal precio, Integer stock, String botica, String distrito,
                                 BigDecimal distanciaKm) {

    static final int MAX_TEXTO = 80;

    public ProductoParaModelo {
        nombre = recortar(nombre);
        principioActivo = recortar(principioActivo);
        presentacion = recortar(presentacion);
        botica = recortar(botica);
        distrito = recortar(distrito);
    }

    static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= MAX_TEXTO ? limpio : limpio.substring(0, MAX_TEXTO);
    }
}
