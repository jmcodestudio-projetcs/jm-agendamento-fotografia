package com.jmcodestudio.agendamento.agenda.exception;

public class BloqueioConflitanteException extends RuntimeException {

    public BloqueioConflitanteException() {
        super("este período conflita com um compromisso ou bloqueio existente na agenda");
    }
}
