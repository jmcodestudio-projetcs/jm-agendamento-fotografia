package com.jmcodestudio.agendamento.profile.exception;

public class SlugJaEmUsoException extends RuntimeException {

    public SlugJaEmUsoException() {
        super("este endereço já está em uso por outro fotógrafo");
    }
}
