package com.jmcodestudio.agendamento.publicpage;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Única fonte de intervalos ocupados usada pelo motor de disponibilidade.
 * calendar_occupancy já é alimentada por trigger tanto a partir de
 * calendar_blocks quanto de appointments (CONFIRMADO/PENDENTE_APROVACAO/
 * REALIZADO, ver V1) — não precisamos ler essas tabelas de origem
 * diretamente para calcular disponibilidade.
 */
@Repository
public class PublicOccupancyRepository {

    private static final RowMapper<OccupiedInterval> MAPPER = (rs, rowNum) -> new OccupiedInterval(
            rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
            rs.getObject("ends_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbcTemplate;

    public PublicOccupancyRepository(@Qualifier("publicDataSource") DataSource publicDataSource) {
        this.jdbcTemplate = new JdbcTemplate(publicDataSource);
    }

    /**
     * Intervalos de ocupação que tocam a janela [from, to) — janela
     * generosa (o dia todo no fuso do fotógrafo); o AvailabilityCalculator
     * faz o teste de sobreposição exato por slot.
     *
     * Bind como OffsetDateTime, não Instant: o driver do Postgres não
     * consegue inferir o tipo SQL para java.time.Instant (mesma convenção
     * de CalendarBlockRepository, pacote agenda) — a conversão fica aqui,
     * na borda com o banco; o resto do motor de disponibilidade usa Instant.
     */
    public List<OccupiedInterval> findOverlapping(UUID photographerId, Instant from, Instant to) {
        return jdbcTemplate.query("""
                SELECT starts_at, ends_at
                FROM scheduling.calendar_occupancy
                WHERE photographer_id = ? AND starts_at < ? AND ends_at > ?
                ORDER BY starts_at ASC
                """,
                MAPPER,
                photographerId,
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
    }
}
