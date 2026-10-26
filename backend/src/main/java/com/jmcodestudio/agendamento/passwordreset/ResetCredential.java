package com.jmcodestudio.agendamento.passwordreset;

import java.util.UUID;

public record ResetCredential(UUID photographerUserId, UUID photographerId,
        String photographerName, String email) {
}
