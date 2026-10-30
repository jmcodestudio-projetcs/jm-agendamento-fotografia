package com.jmcodestudio.agendamento.catalog.exception;

public class PrecoPublicoSemValorException extends RuntimeException {

    public PrecoPublicoSemValorException() {
        super("não é possível tornar o preço público sem informar um valor");
    }
}
