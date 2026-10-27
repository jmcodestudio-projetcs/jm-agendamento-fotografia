package com.jmcodestudio.agendamento.profile.exception;

public class InvalidWhatsappException extends RuntimeException {

    public InvalidWhatsappException() {
        super("whatsapp inválido");
    }
}
