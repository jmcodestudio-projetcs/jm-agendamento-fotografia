package com.jmcodestudio.agendamento.trial;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC puro usando o DataSource do worker, para o ciclo de vida do período de teste.
 */
@Repository
public class TrialLifecycleRepository {

    private static final RowMapper<TrialCandidate> CANDIDATE_MAPPER = (rs, rowNum) -> new TrialCandidate(
            rs.getObject(1, UUID.class),
            rs.getObject(2, UUID.class),
            rs.getString(3),
            rs.getString(4),
            rs.getTimestamp(5).toInstant());

    private final JdbcTemplate jdbcTemplate;

    public TrialLifecycleRepository(@Qualifier("workerDataSource") DataSource workerDataSource) {
        this.jdbcTemplate = new JdbcTemplate(workerDataSource);
    }

    public List<TrialCandidate> findDueForSevenDayWarning() {
        return jdbcTemplate.query("""
                SELECT p.id, s.id, p.name, p.email, s.trial_ends_at
                FROM scheduling.subscriptions s
                JOIN scheduling.photographers p ON p.id = s.photographer_id
                WHERE s.status = 'TRIAL'
                  AND p.account_status = 'EM_TESTE'
                  AND s.trial_warning_7d_sent_at IS NULL
                  AND s.trial_ends_at > now()
                  AND s.trial_ends_at <= now() + interval '7 days'
                """, CANDIDATE_MAPPER);
    }

    public List<TrialCandidate> findDueForOneDayWarning() {
        return jdbcTemplate.query("""
                SELECT p.id, s.id, p.name, p.email, s.trial_ends_at
                FROM scheduling.subscriptions s
                JOIN scheduling.photographers p ON p.id = s.photographer_id
                WHERE s.status = 'TRIAL'
                  AND p.account_status = 'EM_TESTE'
                  AND s.trial_warning_1d_sent_at IS NULL
                  AND s.trial_ends_at > now()
                  AND s.trial_ends_at <= now() + interval '1 day'
                """, CANDIDATE_MAPPER);
    }

    public List<TrialCandidate> findExpired() {
        return jdbcTemplate.query("""
                SELECT p.id, s.id, p.name, p.email, s.trial_ends_at
                FROM scheduling.subscriptions s
                JOIN scheduling.photographers p ON p.id = s.photographer_id
                WHERE s.status = 'TRIAL'
                  AND p.account_status = 'EM_TESTE'
                  AND s.trial_ends_at <= now()
                """, CANDIDATE_MAPPER);
    }

    public void markSevenDayWarningSent(UUID subscriptionId) {
        jdbcTemplate.update("""
                UPDATE scheduling.subscriptions
                SET trial_warning_7d_sent_at = now()
                WHERE id = ?
                """, subscriptionId);
    }

    public void markOneDayWarningSent(UUID subscriptionId) {
        jdbcTemplate.update("""
                UPDATE scheduling.subscriptions
                SET trial_warning_1d_sent_at = now()
                WHERE id = ?
                """, subscriptionId);
    }

    /**
     * Idempotente: os filtros de status fazem o UPDATE afetar 0 linhas se outra
     * execução já suspendeu a conta.
     */
    @Transactional(transactionManager = "workerTransactionManager")
    public void suspendForTrialEnded(UUID photographerId, UUID subscriptionId) {
        jdbcTemplate.update("""
                UPDATE scheduling.photographers
                SET account_status = 'SUSPENSA',
                    suspended_at = now(),
                    suspension_reason = 'TRIAL_ENDED'
                WHERE id = ? AND account_status = 'EM_TESTE'
                """, photographerId);
        jdbcTemplate.update("""
                UPDATE scheduling.subscriptions
                SET status = 'ENDED', updated_at = now()
                WHERE id = ? AND status = 'TRIAL'
                """, subscriptionId);
    }
}
