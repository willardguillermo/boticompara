package com.boticompara.usuario.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Formato de error de docs/API.md. {@code codigo} solo viene en los errores que la web o la app deben reconocer sin
 * comparar el mensaje (por ahora CORREO_NO_VERIFICADO en el login); si es null no aparece en el JSON.
 */
public record ErrorResponse(int status, String error, String mensaje,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String codigo, Instant timestamp) {

    public static ErrorResponse de(HttpStatus status, String mensaje) {
        return de(status, mensaje, null);
    }

    public static ErrorResponse de(HttpStatus status, String mensaje, String codigo) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), mensaje, codigo, Instant.now());
    }
}
