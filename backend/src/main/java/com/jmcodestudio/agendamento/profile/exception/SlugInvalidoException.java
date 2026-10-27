package com.jmcodestudio.agendamento.profile.exception;

public class SlugInvalidoException extends RuntimeException {

    public SlugInvalidoException() {
        super("slug inválido: use apenas letras minúsculas, números e hífen, entre 3 e 100 caracteres, sem hífen no início/fim ou repetido");
    }
}
