package com.jmcodestudio.agendamento.signup.exception;

public class TimezoneInvalidaException extends RuntimeException {

    public TimezoneInvalidaException() {
        super("timezone inválido");
    }
}
