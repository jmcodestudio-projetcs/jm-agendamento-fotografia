package com.jmcodestudio.agendamento.passwordreset.exception;

/**
 * Token de redefinição inexistente, expirado, revogado ou já usado. Vira resposta
 * HTTP de erro no endpoint de confirm: o token já prova posse do e-mail, então
 * não há risco de enumeração de contas.
 */
public class InvalidOrExpiredResetTokenException extends RuntimeException {

    public InvalidOrExpiredResetTokenException() {
        super("token de redefinição invalido, expirado ou ja usado");
    }
}
