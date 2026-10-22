package com.jmcodestudio.agendamento.login;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deriva, via HMAC-SHA256, a subject_key (e-mail normalizado) e a origin_key (IP)
 * da tabela access_attempts, sem guardar o dado original em claro.
 * Ver claude/fase-1.2-login-fotografo.md, decisão 4.
 */
@Component
public class AccessAttemptHmac {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec keySpec;

    public AccessAttemptHmac(@Value("${ACCESS_ATTEMPTS_HMAC_KEY}") String base64Key) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ACCESS_ATTEMPTS_HMAC_KEY não é uma string base64 válida.", e);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "ACCESS_ATTEMPTS_HMAC_KEY deve ter pelo menos " + MIN_KEY_BYTES
                            + " bytes após decodificar o base64 (atual: " + key.length + ").");
        }
        this.keySpec = new SecretKeySpec(key, ALGORITHM);
    }

    public byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular HMAC-SHA256.", e);
        }
    }
}
