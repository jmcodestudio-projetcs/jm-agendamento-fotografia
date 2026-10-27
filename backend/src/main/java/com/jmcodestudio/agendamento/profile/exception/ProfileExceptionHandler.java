package com.jmcodestudio.agendamento.profile.exception;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import com.jmcodestudio.agendamento.signup.exception.TimezoneInvalidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProfileExceptionHandler {

    @ExceptionHandler(InvalidWhatsappException.class)
    public ResponseEntity<Map<String, String>> handleInvalidWhatsapp(InvalidWhatsappException e) {
        return body(HttpStatus.BAD_REQUEST, "WHATSAPP_INVALIDO", e.getMessage());
    }

    @ExceptionHandler(TimezoneInvalidaException.class)
    public ResponseEntity<Map<String, String>> handleTimezone(TimezoneInvalidaException e) {
        return body(HttpStatus.BAD_REQUEST, "TIMEZONE_INVALIDO", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", message);
    }

    // Defensivo: o id vem de uma sessão autenticada válida, então não deveria ocorrer.
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return body(HttpStatus.NOT_FOUND, "FOTOGRAFO_NAO_ENCONTRADO", "Fotógrafo não encontrado.");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }
}
