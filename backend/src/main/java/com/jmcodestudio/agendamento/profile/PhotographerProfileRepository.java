package com.jmcodestudio.agendamento.profile;

import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PhotographerProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public PhotographerProfileRepository(@Qualifier("tenantDataSource") DataSource tenantDataSource) {
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

    public Optional<PhotographerProfile> findById(UUID photographerId) {
        try {
            // Keep the explicit ID predicate as defense in depth in addition to RLS.
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT name, email, whatsapp, timezone, public_slug
                    FROM scheduling.photographers
                    WHERE id = ?
                    """,
                    (rs, rowNum) -> new PhotographerProfile(
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("whatsapp"),
                            rs.getString("timezone"),
                            rs.getString("public_slug")),
                    photographerId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void update(
            UUID photographerId,
            String name,
            String whatsapp,
            String whatsappNormalized,
            String timezone) {
        // Keep the explicit ID predicate as defense in depth in addition to RLS.
        jdbcTemplate.update(
                "UPDATE scheduling.photographers SET name = ?, whatsapp = ?, whatsapp_normalized = ?, timezone = ? WHERE id = ?",
                name,
                whatsapp,
                whatsappNormalized,
                timezone,
                photographerId);
    }

    public void updateSlug(UUID photographerId, String slug) {
        jdbcTemplate.update(
                "UPDATE scheduling.photographers SET public_slug = ? WHERE id = ?",
                slug,
                photographerId);
    }
}
