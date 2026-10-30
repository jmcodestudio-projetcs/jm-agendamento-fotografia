package com.jmcodestudio.agendamento.agenda;

import java.time.LocalDate;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingHorizonRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookingHorizonRepository(@Qualifier("tenantDataSource") DataSource tenantDataSource) {
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

    public TimezoneAndHorizon findTimezoneAndHorizon(UUID photographerId) {
        return jdbcTemplate.queryForObject("""
                SELECT timezone, booking_horizon_date
                FROM scheduling.photographers
                WHERE id = ?
                """,
                (rs, rowNum) -> new TimezoneAndHorizon(
                        rs.getString("timezone"),
                        rs.getObject("booking_horizon_date", LocalDate.class)),
                photographerId);
    }

    public int updateHorizon(UUID photographerId, LocalDate bookingHorizonDate) {
        return jdbcTemplate.update(
                "UPDATE scheduling.photographers SET booking_horizon_date = ?, updated_at = now() WHERE id = ?",
                bookingHorizonDate,
                photographerId);
    }

    public record TimezoneAndHorizon(String timezone, LocalDate bookingHorizonDate) {
    }
}
