package com.jmcodestudio.agendamento.login.exception;

public class AccountSecurityBlockedException extends LoginDeniedException {

    public AccountSecurityBlockedException() {
        super("conta com bloqueio de seguranca (security_blocked_at preenchido)");
    }
}
