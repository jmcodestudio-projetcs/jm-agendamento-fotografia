package com.jmcodestudio.agendamento.passwordreset;

import jakarta.validation.constraints.NotBlank;

// Tamanho e checagem de senha comum aplicados em PasswordResetService.validateNewPassword,
// com as mesmas regras do cadastro (password_min_chars/password_max_chars de
// platform_settings + CommonPasswordChecker) -- nao mais um @Size fixo e solto aqui
// (achado A-4 da auditoria de 30/10/2026: o reset aceitava senha mais fraca do que
// o cadastro permitiria).
public record PasswordResetConfirmDto(
        @NotBlank String token,
        @NotBlank String newPassword) {
}
