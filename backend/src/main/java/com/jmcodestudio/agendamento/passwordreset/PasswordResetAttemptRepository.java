package com.jmcodestudio.agendamento.passwordreset;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC puro (sem JPA) usando o role de privilégio mínimo da recuperação de acesso.
 * O scope é repassado como veio (para este repositório, sempre "RESET"); o CHECK
 * constraint do banco rejeita valores inválidos. A janela em segundos é sempre
 * informada pelo chamador (3600, 1 hora, fixa; não vem de platform_settings).
 */
@Repository
public class PasswordResetAttemptRepository {

    private final JdbcTemplate jdbcTemplate;

    public PasswordResetAttemptRepository(@Qualifier("resetDataSource") DataSource resetDataSource) {
        this.jdbcTemplate = new JdbcTemplate(resetDataSource);
    }

    public int countRecentSubjectFailures(String scope, byte[] subjectKey, int windowSeconds) {
        return countRecentFailures("subject_key", scope, subjectKey, windowSeconds);
    }

    public int countRecentOriginFailures(String scope, byte[] originKey, int windowSeconds) {
        return countRecentFailures("origin_key", scope, originKey, windowSeconds);
    }

    // No reset, cada pedido grava uma linha: o limite conta todas as tentativas, não só falhas.
    public int countRecentSubjectAttempts(String scope, byte[] subjectKey, int windowSeconds) {
        return countRecentAttempts("subject_key", scope, subjectKey, windowSeconds);
    }

    public int countRecentOriginAttempts(String scope, byte[] originKey, int windowSeconds) {
        return countRecentAttempts("origin_key", scope, originKey, windowSeconds);
    }

    private int countRecentAttempts(String keyColumn, String scope, byte[] key, int windowSeconds) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM scheduling.access_attempts
                WHERE scope = ? AND %s = ?
                  AND occurred_at > now() - (? * interval '1 second')
                """.formatted(keyColumn),
                Integer.class, scope, key, windowSeconds);
        return count == null ? 0 : count;
    }

    // keyColumn é sempre um literal interno, nunca entrada externa.
    private int countRecentFailures(String keyColumn, String scope, byte[] key, int windowSeconds) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM scheduling.access_attempts
                WHERE scope = ? AND %s = ? AND succeeded = false
                  AND occurred_at > now() - (? * interval '1 second')
                """.formatted(keyColumn),
                Integer.class, scope, key, windowSeconds);
        return count == null ? 0 : count;
    }

    public void recordAttempt(String scope, UUID photographerIdOrNull, byte[] subjectKey,
            byte[] originKey, boolean succeeded) {
        jdbcTemplate.update("""
                INSERT INTO scheduling.access_attempts
                    (id, scope, photographer_id, subject_key, origin_key, succeeded)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), scope, photographerIdOrNull, subjectKey, originKey, succeeded);
    }
}
