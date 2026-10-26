package com.jmcodestudio.agendamento.passwordreset;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC puro (sem JPA) usando o role de privilégio mínimo da recuperação de acesso.
 * Só o hash do token é persistido, nunca o token em claro.
 */
@Repository
public class PasswordResetTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public PasswordResetTokenRepository(@Qualifier("resetDataSource") DataSource resetDataSource) {
        this.jdbcTemplate = new JdbcTemplate(resetDataSource);
    }

    public UUID create(UUID photographerUserId, byte[] tokenHash, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO scheduling.password_reset_tokens
                    (id, photographer_user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """,
                id, photographerUserId, tokenHash, Timestamp.from(expiresAt));
        return id;
    }

    public Optional<ActiveResetToken> findActiveByTokenHash(byte[] tokenHash) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT t.id, t.photographer_user_id, pu.photographer_id
                    FROM scheduling.password_reset_tokens t
                    JOIN scheduling.photographer_users pu ON pu.id = t.photographer_user_id
                    WHERE t.token_hash = ? AND t.consumed_at IS NULL AND t.revoked_at IS NULL
                      AND t.expires_at > now()
                    """,
                    (rs, rowNum) -> new ActiveResetToken(
                            rs.getObject("id", UUID.class),
                            rs.getObject("photographer_user_id", UUID.class),
                            rs.getObject("photographer_id", UUID.class)),
                    tokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Retorna false se o token já foi consumido, revogado ou expirou entre a
     * validação e este UPDATE (fecha a condição de corrida do uso único).
     */
    public boolean consume(UUID tokenId) {
        int updated = jdbcTemplate.update("""
                UPDATE scheduling.password_reset_tokens SET consumed_at = now()
                WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL
                  AND expires_at > now()
                """,
                tokenId);
        return updated == 1;
    }

    public void revokeOtherActiveTokens(UUID photographerUserId, UUID exceptTokenId) {
        jdbcTemplate.update("""
                UPDATE scheduling.password_reset_tokens SET revoked_at = now()
                WHERE photographer_user_id = ? AND id <> ?
                  AND consumed_at IS NULL AND revoked_at IS NULL
                """,
                photographerUserId, exceptTokenId);
    }
}
