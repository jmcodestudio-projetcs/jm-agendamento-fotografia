package com.jmcodestudio.agendamento.passwordreset;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // Garantia anti-enumeração: o service nunca lança neste fluxo (e-mail inexistente e
    // rate limit retornam silenciosamente), então a resposta é sempre a mesma 200 genérica.
    @PostMapping("/api/public/photographers/password-reset/request")
    public ResponseEntity<Map<String, String>> request(
            @Valid @RequestBody PasswordResetRequestDto request, HttpServletRequest httpRequest) {
        passwordResetService.requestReset(request.email(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of(
                "message", "Se esse e-mail existir na nossa base, enviamos um link de redefinição."));
    }

    @PostMapping("/api/public/photographers/password-reset/confirm")
    public ResponseEntity<Map<String, String>> confirm(@Valid @RequestBody PasswordResetConfirmDto request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Senha redefinida. Faca login com a nova senha."));
    }
}
