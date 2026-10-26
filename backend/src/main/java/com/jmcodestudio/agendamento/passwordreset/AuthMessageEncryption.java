package com.jmcodestudio.agendamento.passwordreset;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra (AES-256-GCM) o conteúdo das mensagens de autenticação, incluindo o token
 * de recuperação em claro, entre a criação da mensagem e o envio efetivo pelo
 * worker. A tabela authentication_messages não deve guardar nenhuma cópia legível
 * do token (ver claude/fase-1.3-recuperacao-acesso.md, decisão 6).
 *
 * <p>A rotação de chave ainda não está implementada: só o keyId atual é aceito
 * na decifragem, e qualquer outro falha em vez de tentar adivinhar a chave.
 */
@Component
public class AuthMessageEncryption {

    public static final String CURRENT_KEY_ID = "v1";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthMessageEncryption(@Value("${AUTH_MESSAGE_ENCRYPTION_KEY}") String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "AUTH_MESSAGE_ENCRYPTION_KEY não é uma string base64 válida.", e);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "AUTH_MESSAGE_ENCRYPTION_KEY deve ter exatamente " + KEY_LENGTH_BYTES
                            + " bytes após decodificar base64 (AES-256), mas tem "
                            + keyBytes.length + ".");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public EncryptedPayload encrypt(byte[] plaintext) {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] result = new byte[NONCE_LENGTH_BYTES + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, NONCE_LENGTH_BYTES);
            System.arraycopy(encrypted, 0, result, NONCE_LENGTH_BYTES, encrypted.length);
            return new EncryptedPayload(result, CURRENT_KEY_ID);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar mensagem de autenticação.", e);
        }
    }

    public byte[] decrypt(byte[] ciphertextWithNonce, String keyId) {
        if (!CURRENT_KEY_ID.equals(keyId)) {
            throw new IllegalStateException(
                    "keyId '" + keyId + "' não suportado; apenas '" + CURRENT_KEY_ID
                            + "' (rotação de chave ainda não implementada).");
        }
        if (ciphertextWithNonce == null || ciphertextWithNonce.length <= NONCE_LENGTH_BYTES) {
            throw new IllegalStateException("Payload cifrado inválido: tamanho insuficiente.");
        }
        byte[] nonce = Arrays.copyOfRange(ciphertextWithNonce, 0, NONCE_LENGTH_BYTES);
        byte[] encrypted = Arrays.copyOfRange(
                ciphertextWithNonce, NONCE_LENGTH_BYTES, ciphertextWithNonce.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar mensagem de autenticação.", e);
        }
    }
}
