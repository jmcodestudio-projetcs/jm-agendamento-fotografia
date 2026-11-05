package com.jmcodestudio.agendamento.publicpage.exception;

import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Respostas genéricas de propósito: a página pública não deve distinguir
 * "fotógrafo não existe" de "conta indisponível" de "serviço não existe"
 * por código de erro específico — mesmo princípio de "não revelar" já usado
 * em login/reset (RN-029/030, RNF-001).
 */
@RestControllerAdvice(basePackages = "com.jmcodestudio.agendamento.publicpage")
public class PublicCatalogExceptionHandler {

    @ExceptionHandler(FotografoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleFotografoNaoEncontrado() {
        return notFound();
    }

    @ExceptionHandler(ServicoNaoDisponivelException.class)
    public ResponseEntity<Map<String, String>> handleServicoNaoDisponivel() {
        return notFound();
    }

    @ExceptionHandler({ DateTimeParseException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class })
    public ResponseEntity<Map<String, String>> handleDadosInvalidos() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "DADOS_INVALIDOS", "message", "parâmetro inválido"));
    }

    private static ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NAO_ENCONTRADO", "message", "não encontrado"));
    }
}
