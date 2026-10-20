package com.jmcodestudio.agendamento.signup;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC puro (sem JPA) usando o role de privilégio mínimo do cadastro público.
 * Exceções de violação de constraint sobem sem tratamento; o service decide.
 */
@Repository
public class PhotographerSignupRepository {

    private final JdbcTemplate jdbcTemplate;

    public PhotographerSignupRepository(@Qualifier("signupDataSource") DataSource signupDataSource) {
        this.jdbcTemplate = new JdbcTemplate(signupDataSource);
    }

    @Transactional(transactionManager = "signupTransactionManager")
    public UUID insertNewPhotographer(String name, String emailOriginal, String emailNormalized,
            String passwordHash, String timezone, String slugCandidate) {
        UUID photographerId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO scheduling.photographers
                    (id, name, email, email_normalized, public_slug, timezone,
                     account_status, public_page_enabled, push_enabled)
                VALUES (?, ?, ?, ?, ?, ?, 'EM_TESTE', true, false)
                """,
                photographerId, name, emailOriginal, emailNormalized, slugCandidate, timezone);

        jdbcTemplate.update("""
                INSERT INTO scheduling.photographer_users
                    (id, photographer_id, email, email_normalized, password_hash)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), photographerId, emailOriginal, emailNormalized, passwordHash);

        jdbcTemplate.update("""
                INSERT INTO scheduling.subscriptions
                    (id, photographer_id, status, trial_started_at, trial_ends_at)
                VALUES (?, ?, 'TRIAL', now(), now() + interval '30 days')
                """,
                UUID.randomUUID(), photographerId);

        return photographerId;
    }
}
