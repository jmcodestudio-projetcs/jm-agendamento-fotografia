package com.jmcodestudio.agendamento.passwordreset;

/**
 * Resultado da cifragem: {@code ciphertext} contém nonce (12 bytes) seguido do
 * texto cifrado com tag de autenticação GCM; {@code keyId} identifica a chave usada.
 */
public record EncryptedPayload(byte[] ciphertext, String keyId) {
}
