package com.boticompara.usuario.chat;

import com.boticompara.usuario.error.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * H68: el 429 del chat necesita el header Retry-After, que GlobalExceptionHandler no sabe poner.
 * Solo aplica a ChatController (assignableTypes) y solo a LimiteChatException: el resto de la API, y los demás
 * errores del chat (400, 503, 504), siguen pasando por GlobalExceptionHandler. El @Order hace que se consulte
 * antes que el manejador general, que si no atraparía la excepción como un 500.
 */
@RestControllerAdvice(assignableTypes = ChatController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatErrores {

    @ExceptionHandler(LimiteChatException.class)
    public ResponseEntity<ErrorResponse> limite(LimiteChatException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getReintentarEnSegundos()))
                .body(ErrorResponse.de(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }
}
