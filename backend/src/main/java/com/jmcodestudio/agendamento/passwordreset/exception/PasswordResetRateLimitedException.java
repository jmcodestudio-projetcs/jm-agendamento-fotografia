package com.jmcodestudio.agendamento.passwordreset.exception;

/**
 * Limite de pedidos de recuperação excedido por conta ou por IP. Capturada
 * internamente pelo service para pular a criação do token; o endpoint de request
 * continua respondendo 200 genérico.
 */
public class PasswordResetRateLimitedException extends RuntimeException {

    public PasswordResetRateLimitedException() {
        super();
    }
}
