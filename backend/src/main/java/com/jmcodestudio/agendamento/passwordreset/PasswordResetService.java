package com.jmcodestudio.agendamento.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jmcodestudio.agendamento.login.AccessAttemptHmac;
import com.jmcodestudio.agendamento.passwordreset.exception.InvalidOrExpiredResetTokenException;
import com.jmcodestudio.agendamento.signup.PlatformSettingsService;

@Service
public class PasswordResetService {

    private static final String SCOPE = "RESET";
    // Janela fixa de 1h, implícita em reset_per_account_hour/reset_per_ip_hour.
    private static final int WINDOW_SECONDS = 3600;
    private static final int RAW_TOKEN_BYTES = 32;

    private final PasswordResetCredentialRepository credentialRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetAttemptRepository attemptRepository;
    private final PasswordResetMessageWriter messageWriter;
    private final AccessAttemptHmac accessAttemptHmac;
    private final AuthMessageEncryption authMessageEncryption;
    private final PasswordEncoder passwordEncoder;
    private final PlatformSettingsService platformSettingsService;
    private final SessionInvalidationService sessionInvalidationService;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PasswordResetCredentialRepository credentialRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetAttemptRepository attemptRepository,
            PasswordResetMessageWriter messageWriter,
            AccessAttemptHmac accessAttemptHmac,
            AuthMessageEncryption authMessageEncryption,
            PasswordEncoder passwordEncoder,
            PlatformSettingsService platformSettingsService,
            SessionInvalidationService sessionInvalidationService,
            @Value("${FRONTEND_BASE_URL}") String frontendBaseUrl) {
        this.credentialRepository = credentialRepository;
        this.tokenRepository = tokenRepository;
        this.attemptRepository = attemptRepository;
        this.messageWriter = messageWriter;
        this.accessAttemptHmac = accessAttemptHmac;
        this.authMessageEncryption = authMessageEncryption;
        this.passwordEncoder = passwordEncoder;
        this.platformSettingsService = platformSettingsService;
        this.sessionInvalidationService = sessionInvalidationService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    // Nunca revela se o e-mail existe ou se houve limitação: sempre retorna normalmente.
    @Transactional(transactionManager = "resetTransactionManager")
    public void requestReset(String email, String clientIp) {
        String emailNormalized = email.trim().toLowerCase(Locale.ROOT);
        byte[] subjectKey = accessAttemptHmac.hmac(emailNormalized);
        byte[] originKey = accessAttemptHmac.hmac(clientIp);

        int perAccount = platformSettingsService.getIntSetting("reset_per_account_hour");
        int perIp = platformSettingsService.getIntSetting("reset_per_ip_hour");

        if (attemptRepository.countRecentSubjectAttempts(SCOPE, subjectKey, WINDOW_SECONDS) >= perAccount
                || attemptRepository.countRecentOriginAttempts(SCOPE, originKey, WINDOW_SECONDS) >= perIp) {
            attemptRepository.recordAttempt(SCOPE, null, subjectKey, originKey, false);
            return;
        }

        ResetCredential credential = credentialRepository.findByEmailNormalized(emailNormalized)
                .orElse(null);
        if (credential == null) {
            attemptRepository.recordAttempt(SCOPE, null, subjectKey, originKey, false);
            return;
        }

        byte[] rawToken = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(rawToken);
        int ttlSeconds = platformSettingsService.getIntSetting("reset_ttl_seconds");
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        UUID tokenId = tokenRepository.create(credential.photographerUserId(), sha256(rawToken), expiresAt);

        String link = frontendBaseUrl + "/redefinir-senha?token="
                + Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
        String plaintext = "{\"subject\":\"Redefinição de senha\",\"body\":\""
                + "Use o link para redefinir sua senha: " + jsonEscape(link) + "\"}";

        EncryptedPayload payload = authMessageEncryption.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
        messageWriter.enqueue(tokenId, credential.email(), payload.ciphertext(), payload.keyId(), expiresAt);

        attemptRepository.recordAttempt(SCOPE, credential.photographerId(), subjectKey, originKey, true);
    }

    @Transactional(transactionManager = "resetTransactionManager")
    public void confirmReset(String rawToken, String newPassword) {
        byte[] tokenBytes;
        try {
            tokenBytes = Base64.getUrlDecoder().decode(rawToken);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrExpiredResetTokenException();
        }

        ActiveResetToken token = tokenRepository.findActiveByTokenHash(sha256(tokenBytes))
                .orElseThrow(InvalidOrExpiredResetTokenException::new);

        if (!tokenRepository.consume(token.id())) {
            throw new InvalidOrExpiredResetTokenException();
        }

        credentialRepository.updatePasswordHash(token.photographerUserId(), passwordEncoder.encode(newPassword));
        tokenRepository.revokeOtherActiveTokens(token.photographerUserId(), token.id());
        sessionInvalidationService.invalidateAllSessionsFor(token.photographerId());
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
