package com.boticompara.usuario.auth;

import com.boticompara.usuario.controller.AuthController;
import com.boticompara.usuario.error.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** SEC-05: el 429 del login lleva Retry-After. Solo aplica a AuthController. */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class ErroresLogin {

    @ExceptionHandler(LimiteLoginException.class)
    public ResponseEntity<ErrorResponse> limite(LimiteLoginException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getReintentarEnSegundos()))
                .body(ErrorResponse.de(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }
}