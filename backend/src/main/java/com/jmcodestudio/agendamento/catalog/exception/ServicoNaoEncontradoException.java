package com.jmcodestudio.agendamento.catalog.exception;

public class ServicoNaoEncontradoException extends RuntimeException {

    public ServicoNaoEncontradoException() {
        super("serviço não encontrado");
    }
}
