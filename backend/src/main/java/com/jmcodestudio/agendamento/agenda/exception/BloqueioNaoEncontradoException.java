package com.jmcodestudio.agendamento.agenda.exception;

public class BloqueioNaoEncontradoException extends RuntimeException {

    public BloqueioNaoEncontradoException() {
        super("bloqueio não encontrado");
    }
}
