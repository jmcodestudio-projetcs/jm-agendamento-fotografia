package com.jmcodestudio.agendamento.login;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC puro (sem JPA) usando o role de privilégio mínimo do login.
 * O scope é repassado como veio; o CHECK constraint do banco rejeita valores inválidos.
 */
@Repository
public class AccessAttemptRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccessAttemptRepository(@Qualifier("loginDataSource") DataSource loginDataSource) {
        this.jdbcTemplate = new JdbcTemplate(loginDataSource);
    }

    public int countRecentSubjectFailures(String scope, byte[] subjectKey, int windowSeconds) {
        return countRecentFailures("subject_key", scope, subjectKey, windowSeconds);
    }

    public int countRecentOriginFailures(String scope, byte[] originKey, int windowSeconds) {
        return countRecentFailures("origin_key", scope, originKey, windowSeconds);
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
