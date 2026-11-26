package com.boticompara.usuario.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    /** Código fijo para la web y la app (ErrorResponse.codigo); null en casi todos los errores. */
    private final String codigo;

    public ApiException(HttpStatus status, String mensaje) {
        this(status, mensaje, null);
    }

    public ApiException(HttpStatus status, String mensaje, String codigo) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public static ApiException badRequest(String m) { return new ApiException(HttpStatus.BAD_REQUEST, m); }
    public static ApiException unauthorized(String m) { return new ApiException(HttpStatus.UNAUTHORIZED, m); }
    public static ApiException forbidden(String m) { return new ApiException(HttpStatus.FORBIDDEN, m); }
    public static ApiException notFound(String m) { return new ApiException(HttpStatus.NOT_FOUND, m); }
    public static ApiException conflict(String m) { return new ApiException(HttpStatus.CONFLICT, m); }
    public static ApiException unavailable(String m) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, m); }
}
