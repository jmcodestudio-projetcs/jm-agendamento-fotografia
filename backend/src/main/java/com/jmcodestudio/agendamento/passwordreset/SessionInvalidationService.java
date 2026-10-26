package com.jmcodestudio.agendamento.passwordreset;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Redefinição de senha invalida todas as sessões ativas da conta, para que uma
 * sessão eventualmente comprometida não sobreviva à troca de senha
 * (ver claude/fase-1.3-recuperacao-acesso.md, decisão 9).
 */
@Service
public class SessionInvalidationService {

    // O wildcard casa com o JdbcIndexedSessionRepository autoconfigurado
    // (FindByIndexNameSessionRepository<JdbcSession>).
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SessionInvalidationService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    // O principal name da sessão é o photographerId (ver PhotographerAuthenticationController.login).
    public void invalidateAllSessionsFor(UUID photographerId) {
        sessionRepository.findByPrincipalName(photographerId.toString())
                .keySet()
                .forEach(sessionRepository::deleteById);
    }
}
