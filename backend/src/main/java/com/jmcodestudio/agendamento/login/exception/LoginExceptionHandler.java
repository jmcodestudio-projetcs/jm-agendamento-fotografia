package com.jmcodestudio.agendamento.login.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LoginExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginExceptionHandler.class);

    // Resposta idêntica para qualquer subclasse; a reason fica só no log interno.
    @ExceptionHandler(LoginDeniedException.class)
    public ResponseEntity<Map<String, String>> handleLoginDenied(LoginDeniedException e) {
        log.info("Login negado: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "CREDENCIAIS_INVALIDAS",
                "message", "E-mail ou senha incorretos."));
    }
}
