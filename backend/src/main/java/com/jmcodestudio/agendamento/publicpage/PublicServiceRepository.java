package com.jmcodestudio.agendamento.publicpage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.jmcodestudio.agendamento.catalog.ServiceOffering;

/**
 * Leitura pública de services (Fase 4.1), via jm_public. A policy
 * public_active_services (V10) já filtra active=true no banco; o predicado
 * de photographer_id aqui é defesa em profundidade, mesmo padrão de
 * jm_tenant.
 */
@Repository
public class PublicServiceRepository {

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

    public PublicServiceRepository(@Qualifier("publicDataSource") DataSource publicDataSource) {
        this.jdbcTemplate = new JdbcTemplate(publicDataSource);
    }

    public List<ServiceOffering> findActiveByPhotographer(UUID photographerId) {
        return jdbcTemplate.query("""
                SELECT id, name, description, duration_minutes, price_amount,
                       price_public, minimum_notice_minutes, buffer_after_minutes, active
                FROM scheduling.services
                WHERE photographer_id = ?
                ORDER BY name ASC
                """,
                MAPPER,
                photographerId);
    }

    public Optional<ServiceOffering> findById(UUID photographerId, UUID serviceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT id, name, description, duration_minutes, price_amount,
                           price_public, minimum_notice_minutes, buffer_after_minutes, active
                    FROM scheduling.services
                    WHERE photographer_id = ? AND id = ?
                    """,
                    MAPPER,
                    photographerId, serviceId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
