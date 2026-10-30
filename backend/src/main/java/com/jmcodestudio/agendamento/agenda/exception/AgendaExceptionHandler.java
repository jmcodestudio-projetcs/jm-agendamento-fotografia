package com.jmcodestudio.agendamento.agenda.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgendaExceptionHandler {

    @ExceptionHandler(FaixaInvalidaException.class)
    public ResponseEntity<Map<String, String>> handleFaixaInvalida(FaixaInvalidaException e) {
        return body(HttpStatus.BAD_REQUEST, "FAIXA_INVALIDA", e.getMessage());
    }

    @ExceptionHandler(BloqueioConflitanteException.class)
    public ResponseEntity<Map<String, String>> handleBloqueioConflitante(BloqueioConflitanteException e) {
        return body(HttpStatus.CONFLICT, "BLOQUEIO_CONFLITANTE", e.getMessage());
    }

    @ExceptionHandler(BloqueioNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleBloqueioNaoEncontrado(BloqueioNaoEncontradoException e) {
        return body(HttpStatus.NOT_FOUND, "BLOQUEIO_NAO_ENCONTRADO", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", message);
    }

    /**
     * Rede de segurança para qualquer violação de integridade que não tenha
     * sido traduzida antes (ex.: SQLSTATE 23P01 já é tratado como
     * BloqueioConflitanteException no service; isto aqui cobre o que
     * sobrar — ex.: uma corrida residual na UNIQUE de weekly_schedule, ou um
     * valor numérico fora do que a coluna aceita). Sem isto, a pessoa via um
     * 500 genérico com "Não foi possível salvar" em vez de um erro claro.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return body(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "não foi possível salvar esses dados na agenda");
    }

    /**
     * Corpo malformado ou valor de enum inválido (ex.: blockType fora de
     * TIME_OFF/MANUAL_BLOCK) cai aqui em vez do corpo padrão sem mensagem do
     * Spring Boot.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "corpo da requisição inválido");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }
}
