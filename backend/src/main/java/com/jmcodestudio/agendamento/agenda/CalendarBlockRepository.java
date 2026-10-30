package com.jmcodestudio.agendamento.agenda;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CalendarBlockRepository {

    private static final RowMapper<CalendarBlock> MAPPER = (rs, rowNum) -> new CalendarBlock(
            rs.getObject("id", UUID.class),
            BlockType.valueOf(rs.getString("block_type")),
            rs.getObject("starts_at", OffsetDateTime.class),
            rs.getObject("ends_at", OffsetDateTime.class),
            rs.getString("reason"));

    private final JdbcTemplate jdbcTemplate;

    public CalendarBlockRepository(@Qualifier("tenantDataSource") DataSource tenantDataSource) {
        this.jdbcTemplate = new JdbcTemplate(tenantDataSource);
    }

    /**
     * Sets the RLS context for the current transaction. The third argument must
     * be true (is_local), so the setting applies only to this transaction; it
     * must never be false here. Always call this in the same transaction and
     * connection as the queries that follow, never in an isolated transaction.
     */
    public void setTenantContext(UUID photographerId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('app.photographer_id', ?, true)",
                String.class,
                photographerId.toString());
    }

    /**
     * Insere o bloqueio. Se o intervalo conflitar com um compromisso ou outro
     * bloqueio já ocupando a agenda (RN-002A), o trigger block_occupancy_sync
     * tenta gravar em calendar_occupancy e a exclusion constraint
     * calendar_no_overlap rejeita com SQLSTATE 23P01 — o chamador (Service)
     * converte isso em BloqueioConflitanteException e a transação sofre rollback.
     */
    public CalendarBlock insert(
            UUID photographerId,
            BlockType blockType,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String reason) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO scheduling.calendar_blocks (photographer_id, block_type, starts_at, ends_at, reason)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, block_type, starts_at, ends_at, reason
                """,
                MAPPER,
                photographerId,
                blockType.name(),
                startsAt,
                endsAt,
                reason);
    }

    public List<CalendarBlock> findActiveByPhotographer(UUID photographerId) {
        // "Ativo" aqui significa ainda não encerrado; bloqueios passados somem da tela sem serem excluídos do histórico.
        return jdbcTemplate.query("""
                SELECT id, block_type, starts_at, ends_at, reason
                FROM scheduling.calendar_blocks
                WHERE photographer_id = ? AND ends_at >= now()
                ORDER BY starts_at ASC
                """,
                MAPPER,
                photographerId);
    }

    public int deleteById(UUID photographerId, UUID blockId) {
        // Keep the explicit photographer_id predicate as defense in depth in addition to RLS.
        return jdbcTemplate.update(
                "DELETE FROM scheduling.calendar_blocks WHERE id = ? AND photographer_id = ?",
                blockId,
                photographerId);
    }
}
