package com.jmcodestudio.agendamento.notification;

import java.time.Instant;
import java.util.UUID;

public record ClaimedMessage(UUID id, String recipient, byte[] payloadCiphertext,
        String encryptionKeyId, Instant expiresAt, int attemptCount) {
}
