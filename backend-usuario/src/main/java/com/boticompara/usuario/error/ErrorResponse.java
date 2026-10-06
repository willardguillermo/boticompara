package com.boticompara.usuario.error;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/** Formato de error de docs/API.md */
public record ErrorResponse(int status, String error, String mensaje, Instant timestamp) {

    public static ErrorResponse de(HttpStatus status, String mensaje) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), mensaje, Instant.now());
    }
}
