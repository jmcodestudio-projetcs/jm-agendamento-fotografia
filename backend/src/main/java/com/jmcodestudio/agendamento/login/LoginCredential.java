package com.jmcodestudio.agendamento.login;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoginCredential(
        UUID photographerUserId,
        UUID photographerId,
        String photographerName,
        String passwordHash,
        OffsetDateTime securityBlockedAt) {
}
