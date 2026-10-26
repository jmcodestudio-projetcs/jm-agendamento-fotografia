package com.jmcodestudio.agendamento.passwordreset;

import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC puro (sem JPA) usando o role de privilégio mínimo da recuperação de acesso.
 */
@Repository
public class PasswordResetCredentialRepository {

    private final JdbcTemplate jdbcTemplate;

    public PasswordResetCredentialRepository(@Qualifier("resetDataSource") DataSource resetDataSource) {
        this.jdbcTemplate = new JdbcTemplate(resetDataSource);
    }

    public Optional<ResetCredential> findByEmailNormalized(String emailNormalized) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT pu.id, pu.photographer_id, p.name, pu.email
                    FROM scheduling.photographer_users pu
                    JOIN scheduling.photographers p ON p.id = pu.photographer_id
                    WHERE pu.email_normalized = ?
                    """,
                    (rs, rowNum) -> new ResetCredential(
                            rs.getObject("id", UUID.class),
                            rs.getObject("photographer_id", UUID.class),
                            rs.getString("name"),
                            rs.getString("email")),
                    emailNormalized));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updatePasswordHash(UUID photographerUserId, String newPasswordHash) {
        jdbcTemplate.update(
                "UPDATE scheduling.photographer_users SET password_hash = ? WHERE id = ?",
                newPasswordHash, photographerUserId);
    }
}
