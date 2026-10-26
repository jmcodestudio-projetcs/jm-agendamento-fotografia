package com.jmcodestudio.agendamento.passwordreset;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Só insere em authentication_messages (role de reset, apenas INSERT). Status,
 * available_at e created_at usam os defaults da coluna. A leitura/atualização
 * pelo worker fica em outra classe, com outro DataSource.
 */
@Repository
public class PasswordResetMessageWriter {

    private final JdbcTemplate jdbcTemplate;

    public PasswordResetMessageWriter(@Qualifier("resetDataSource") DataSource resetDataSource) {
        this.jdbcTemplate = new JdbcTemplate(resetDataSource);
    }

    public void enqueue(UUID passwordResetTokenId, String recipientEmail,
            byte[] payloadCiphertext, String encryptionKeyId, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO scheduling.authentication_messages
                    (id, password_reset_token_id, recipient, payload_ciphertext,
                     encryption_key_id, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), passwordResetTokenId, recipientEmail, payloadCiphertext,
                encryptionKeyId, Timestamp.from(expiresAt));
    }
}
