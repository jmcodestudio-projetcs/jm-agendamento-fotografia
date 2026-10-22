package com.jmcodestudio.agendamento.login.exception;

public class LoginRateLimitedException extends LoginDeniedException {

    public LoginRateLimitedException() {
        super("limite de tentativas de login excedido por conta ou por IP");
    }
}
