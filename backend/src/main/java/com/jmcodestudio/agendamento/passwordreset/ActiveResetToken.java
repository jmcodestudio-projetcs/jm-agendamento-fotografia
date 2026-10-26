package com.jmcodestudio.agendamento.passwordreset;

import java.util.UUID;

public record ActiveResetToken(UUID id, UUID photographerUserId, UUID photographerId) {
}
