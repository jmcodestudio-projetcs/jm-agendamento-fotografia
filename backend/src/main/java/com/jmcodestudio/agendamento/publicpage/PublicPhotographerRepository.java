package com.jmcodestudio.agendamento.publicpage;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicPhotographerRepository {

    private final JdbcTemplate jdbcTemplate;

    public PublicPhotographerRepository(@Qualifier("publicDataSource") DataSource publicDataSource) {
        this.jdbcTemplate = new JdbcTemplate(publicDataSource);
    }

    /**
     * Mesmo mecanismo de jm_tenant, reaproveitado para jm_public: toda
     * leitura de catálogo/agenda depois do lookup por slug passa a ser
     * escopada por este contexto (policies FOR SELECT da V10). Deve ser a
     * primeira instrução após o lookup, na mesma transação/conexão.
     */
    public void setPublicContext(UUID photographerId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('app.photographer_id', ?, true)",
                String.class,
                photographerId.toString());
    }

    /**
     * Bootstrap: antes de existir um photographer_id de contexto, a única
     * consulta permitida a jm_public é esta, por slug (policy
     * public_slug_lookup, USING(true), colunas restritas via GRANT na V10).
     */
    public Optional<PublicPhotographer> findBySlug(String slug) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT id, name, timezone, account_status, public_page_enabled, booking_horizon_date
                    FROM scheduling.photographers
                    WHERE public_slug = ?
                    """,
                    (rs, rowNum) -> new PublicPhotographer(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("timezone"),
                            rs.getString("account_status"),
                            rs.getBoolean("public_page_enabled"),
                            rs.getObject("booking_horizon_date", LocalDate.class)),
                    slug));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
