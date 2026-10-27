package com.jmcodestudio.agendamento.trial;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha de photographers + subscriptions relevante para o ciclo de vida do teste
 * (aviso pendente ou teste expirado).
 */
public record TrialCandidate(
        UUID photographerId,
        UUID subscriptionId,
        String name,
        String email,
        Instant trialEndsAt) {
}
