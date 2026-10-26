package com.jmcodestudio.agendamento.passwordreset.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PasswordResetExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetExceptionHandler.class);

    @ExceptionHandler(InvalidOrExpiredResetTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidToken(InvalidOrExpiredResetTokenException e) {
        log.info("Redefinição de senha negada: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "TOKEN_INVALIDO",
                "message", "Este link nao e mais valido. Solicite um novo."));
    }
}
