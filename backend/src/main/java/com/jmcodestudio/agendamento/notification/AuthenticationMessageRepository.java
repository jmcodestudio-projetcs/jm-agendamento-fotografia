package com.jmcodestudio.agendamento.notification;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC puro (sem JPA) usando o role de privilégio mínimo do worker
 * (SELECT/UPDATE em authentication_messages).
 */
@Repository
public class AuthenticationMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthenticationMessageRepository(@Qualifier("workerDataSource") DataSource workerDataSource) {
        this.jdbcTemplate = new JdbcTemplate(workerDataSource);
    }

    /**
     * Este método PRECISA rodar dentro de uma transação (o chamador decide o
     * @Transactional): sem ela, o FOR UPDATE SKIP LOCKED libera as linhas assim que o
     * SELECT termina e dois workers poderiam reivindicar as mesmas mensagens.
     * workerInstanceId deve ser um UUID, pois lock_owner é uuid.
     */
    public List<ClaimedMessage> claimBatch(String workerInstanceId, int batchSize, int leaseSeconds) {
        UUID lockOwner = UUID.fromString(workerInstanceId);

        List<ClaimedMessage> messages = jdbcTemplate.query("""
                SELECT id, recipient, payload_ciphertext, encryption_key_id, expires_at, attempt_count
                FROM scheduling.authentication_messages
                WHERE status = 'PENDING' AND available_at <= now()
                ORDER BY available_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> new ClaimedMessage(
                        rs.getObject("id", UUID.class),
                        rs.getString("recipient"),
                        rs.getBytes("payload_ciphertext"),
                        rs.getString("encryption_key_id"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getInt("attempt_count")),
                batchSize);

        if (messages.isEmpty()) {
            return messages;
        }

        UUID[] ids = messages.stream().map(ClaimedMessage::id).toArray(UUID[]::new);
        jdbcTemplate.execute((java.sql.Connection connection) -> {
            Array idArray = connection.createArrayOf("uuid", ids);
            try (var statement = connection.prepareStatement("""
                    UPDATE scheduling.authentication_messages
                    SET status = 'PROCESSING', lock_owner = ?,
                        locked_until = now() + (? * interval '1 second')
                    WHERE id = ANY(?)
                    """)) {
                statement.setObject(1, lockOwner);
                statement.setInt(2, leaseSeconds);
                statement.setArray(3, idArray);
                return statement.executeUpdate();
            } finally {
                idArray.free();
            }
        });
        return messages;
    }

    public void markSent(UUID id, String providerId) {
        jdbcTemplate.update("""
                UPDATE scheduling.authentication_messages
                SET status = 'SENT', provider_id = ?, lock_owner = NULL, locked_until = NULL
                WHERE id = ?
                """,
                providerId, id);
    }

    public void markFailedForRetry(UUID id, int newAttemptCount, Instant nextAvailableAt,
            String errorMessage) {
        jdbcTemplate.update("""
                UPDATE scheduling.authentication_messages
                SET status = 'PENDING', attempt_count = ?, available_at = ?, last_error = ?,
                    lock_owner = NULL, locked_until = NULL
                WHERE id = ?
                """,
                newAttemptCount, Timestamp.from(nextAvailableAt), errorMessage, id);
    }

    public void markFailedPermanently(UUID id, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE scheduling.authentication_messages
                SET status = 'FAILED', last_error = ?, lock_owner = NULL, locked_until = NULL
                WHERE id = ?
                """,
                errorMessage, id);
    }

    public void markCanceledExpired(UUID id) {
        jdbcTemplate.update("""
                UPDATE scheduling.authentication_messages
                SET status = 'CANCELED', lock_owner = NULL, locked_until = NULL
                WHERE id = ?
                """,
                id);
    }
}
