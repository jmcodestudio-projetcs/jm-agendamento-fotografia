package com.jmcodestudio.agendamento.login.exception;

public class InvalidCredentialsException extends LoginDeniedException {

    public InvalidCredentialsException() {
        super("senha incorreta ou e-mail nao encontrado");
    }
}
