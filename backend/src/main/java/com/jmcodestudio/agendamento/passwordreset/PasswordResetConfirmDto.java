package com.jmcodestudio.agendamento.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// A faixa 15-128 espelha password_min_chars/password_max_chars de platform_settings.
public record PasswordResetConfirmDto(
        @NotBlank String token,
        @NotBlank @Size(min = 15, max = 128) String newPassword) {
}
