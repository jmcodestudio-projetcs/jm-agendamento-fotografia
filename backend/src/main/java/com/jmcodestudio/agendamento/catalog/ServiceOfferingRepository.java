package com.jmcodestudio.agendamento.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceOfferingRepository {

    private static final RowMapper<ServiceOffering> MAPPER = (rs, rowNum) -> new ServiceOffering(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("duration_minutes"),
            rs.getBigDecimal("price_amount"),
            rs.getBoolean("price_public"),
            rs.getInt("minimum_notice_minutes"),
            rs.getInt("buffer_after_minutes"),
            rs.getBoolean("active"));

    private final JdbcTemplate jdbcTemplate;

    public ServiceOfferingRepository(@Qualifier("tenantDataSource") DataSource tenantDataSource) {
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

    public ServiceOffering insert(
            UUID photographerId,
            String name,
            String description,
            int durationMinutes,
            BigDecimal priceAmount,
            boolean pricePublic,
            int minimumNoticeMinutes,
            int bufferAfterMinutes) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO scheduling.services (
                    photographer_id, name, description, duration_minutes, price_amount,
                    price_public, minimum_notice_minutes, buffer_after_minutes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, name, description, duration_minutes, price_amount,
                    price_public, minimum_notice_minutes, buffer_after_minutes, active
                """,
                MAPPER,
                photographerId,
                name,
                description,
                durationMinutes,
                priceAmount,
                pricePublic,
                minimumNoticeMinutes,
                bufferAfterMinutes);
    }

    public List<ServiceOffering> findAllByPhotographer(UUID photographerId) {
        // Keep the explicit photographer_id predicate as defense in depth in addition to RLS.
        return jdbcTemplate.query("""
                SELECT id, name, description, duration_minutes, price_amount, price_public,
                       minimum_notice_minutes, buffer_after_minutes, active
                FROM scheduling.services
                WHERE photographer_id = ?
                ORDER BY name ASC
                """,
                MAPPER,
                photographerId);
    }

    public int update(
            UUID photographerId,
            UUID serviceId,
            String name,
            String description,
            int durationMinutes,
            BigDecimal priceAmount,
            boolean pricePublic,
            int minimumNoticeMinutes,
            int bufferAfterMinutes) {
        // Keep the explicit photographer_id predicate as defense in depth in addition to RLS.
        return jdbcTemplate.update("""
                UPDATE scheduling.services
                SET name = ?, description = ?, duration_minutes = ?, price_amount = ?,
                    price_public = ?, minimum_notice_minutes = ?, buffer_after_minutes = ?,
                    updated_at = now()
                WHERE id = ? AND photographer_id = ?
                """,
                name,
                description,
                durationMinutes,
                priceAmount,
                pricePublic,
                minimumNoticeMinutes,
                bufferAfterMinutes,
                serviceId,
                photographerId);
    }

    public int updateActive(UUID photographerId, UUID serviceId, boolean active) {
        // Keep the explicit photographer_id predicate as defense in depth in addition to RLS.
        return jdbcTemplate.update(
                "UPDATE scheduling.services SET active = ?, updated_at = now() WHERE id = ? AND photographer_id = ?",
                active,
                serviceId,
                photographerId);
    }
}
