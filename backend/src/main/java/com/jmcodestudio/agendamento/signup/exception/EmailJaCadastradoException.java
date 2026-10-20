package com.jmcodestudio.agendamento.signup.exception;

public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException() {
        super("e-mail já cadastrado");
    }
}
