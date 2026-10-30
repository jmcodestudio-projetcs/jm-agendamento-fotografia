package com.jmcodestudio.agendamento.catalog.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(ServicoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleServicoNaoEncontrado(ServicoNaoEncontradoException e) {
        return body(HttpStatus.NOT_FOUND, "SERVICO_NAO_ENCONTRADO", e.getMessage());
    }

    @ExceptionHandler(PrecoPublicoSemValorException.class)
    public ResponseEntity<Map<String, String>> handlePrecoPublicoSemValor(PrecoPublicoSemValorException e) {
        return body(HttpStatus.BAD_REQUEST, "PRECO_PUBLICO_SEM_VALOR", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", message);
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }
}
