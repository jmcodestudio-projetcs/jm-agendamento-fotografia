package com.jmcodestudio.agendamento.signup.exception;

public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String reason) {
        super(reason);
    }
}
