package com.jmcodestudio.agendamento.login.exception;

/**
 * Tipo comum de negação de login. O reason serve apenas para log/observabilidade
 * interna e nunca deve ir na resposta HTTP: todas as subclasses mapeiam para a mesma
 * resposta 401 genérica (claude/fase-1.2-login-fotografo.md, decisão 5).
 */
public abstract class LoginDeniedException extends RuntimeException {

    protected LoginDeniedException(String reason) {
        super(reason);
    }
}
