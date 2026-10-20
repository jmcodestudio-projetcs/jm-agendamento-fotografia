package com.jmcodestudio.agendamento.signup.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SignupExceptionHandler {

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPassword(InvalidPasswordException e) {
        return body(HttpStatus.BAD_REQUEST, "SENHA_INVALIDA", e.getMessage());
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

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<Map<String, String>> handleEmailDuplicado(EmailJaCadastradoException e) {
        return body(HttpStatus.CONFLICT, "EMAIL_JA_CADASTRADO", "Este e-mail já está cadastrado.");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }
}
