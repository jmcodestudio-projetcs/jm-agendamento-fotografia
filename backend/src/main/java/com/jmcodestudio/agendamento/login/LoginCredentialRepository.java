package com.jmcodestudio.agendamento.login;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC puro (sem JPA) usando o role de privilégio mínimo do login.
 */
@Repository
public class LoginCredentialRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoginCredentialRepository(@Qualifier("loginDataSource") DataSource loginDataSource) {
        this.jdbcTemplate = new JdbcTemplate(loginDataSource);
    }

    public Optional<LoginCredential> findByEmailNormalized(String emailNormalized) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT pu.id, pu.photographer_id, p.name, pu.password_hash, p.security_blocked_at
                    FROM scheduling.photographer_users pu
                    JOIN scheduling.photographers p ON p.id = pu.photographer_id
                    WHERE pu.email_normalized = ?
                    """,
                    (rs, rowNum) -> new LoginCredential(
                            rs.getObject("id", UUID.class),
                            rs.getObject("photographer_id", UUID.class),
                            rs.getString("name"),
                            rs.getString("password_hash"),
                            rs.getObject("security_blocked_at", OffsetDateTime.class)),
                    emailNormalized));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void touchLastLogin(UUID photographerUserId) {
        jdbcTemplate.update(
                "UPDATE scheduling.photographer_users SET last_login_at = now() WHERE id = ?",
                photographerUserId);
    }
}
