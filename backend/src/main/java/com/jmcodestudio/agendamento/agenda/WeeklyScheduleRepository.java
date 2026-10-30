package com.jmcodestudio.agendamento.agenda;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WeeklyScheduleRepository {

    private static final RowMapper<WeeklyScheduleEntry> MAPPER = (rs, rowNum) -> new WeeklyScheduleEntry(
            rs.getInt("weekday"),
            rs.getObject("start_time", LocalTime.class),
            rs.getObject("end_time", LocalTime.class));

    private final JdbcTemplate jdbcTemplate;

    public WeeklyScheduleRepository(@Qualifier("tenantDataSource") DataSource tenantDataSource) {
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
     * Trava a linha do fotógrafo até o fim da transação. Segue a orientação do
     * DDL (V1): "escritores de domínio devem bloquear photographers com FOR
     * UPDATE antes de validar e alterar agenda/conta/contatos" — orientação que
     * ainda não existia em nenhum repositório anterior (perfil, catálogo).
     * Chamar isso serializa duas chamadas concorrentes de replaceAll para o
     * mesmo fotógrafo, em vez de deixá-las colidir na UNIQUE.
     */
    public void lockPhotographer(UUID photographerId) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM scheduling.photographers WHERE id = ? FOR UPDATE",
                UUID.class,
                photographerId);
    }

    public List<WeeklyScheduleEntry> findAllByPhotographer(UUID photographerId) {
        // Keep the explicit photographer_id predicate as defense in depth in addition to RLS.
        return jdbcTemplate.query("""
                SELECT weekday, start_time, end_time
                FROM scheduling.weekly_schedule
                WHERE photographer_id = ?
                ORDER BY weekday ASC, start_time ASC
                """,
                MAPPER,
                photographerId);
    }

    /**
     * Substitui a jornada semanal inteira. DELETE + INSERT na mesma transação
     * (chamador garante @Transactional); mais simples e seguro que calcular um
     * diff, e o volume de linhas por fotógrafo é pequeno.
     */
    public void replaceAll(UUID photographerId, List<WeeklyScheduleEntry> entries) {
        jdbcTemplate.update(
                "DELETE FROM scheduling.weekly_schedule WHERE photographer_id = ?",
                photographerId);

        for (WeeklyScheduleEntry entry : entries) {
            jdbcTemplate.update("""
                    INSERT INTO scheduling.weekly_schedule (photographer_id, weekday, start_time, end_time)
                    VALUES (?, ?, ?, ?)
                    """,
                    photographerId,
                    entry.weekday(),
                    entry.startTime(),
                    entry.endTime());
        }
    }
}
