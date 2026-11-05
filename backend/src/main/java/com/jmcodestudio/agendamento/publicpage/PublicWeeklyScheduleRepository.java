package com.jmcodestudio.agendamento.publicpage;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.jmcodestudio.agendamento.agenda.WeeklyScheduleEntry;

@Repository
public class PublicWeeklyScheduleRepository {

    private static final RowMapper<WeeklyScheduleEntry> MAPPER = (rs, rowNum) -> new WeeklyScheduleEntry(
            rs.getInt("weekday"),
            rs.getObject("start_time", LocalTime.class),
            rs.getObject("end_time", LocalTime.class));

    private final JdbcTemplate jdbcTemplate;

    public PublicWeeklyScheduleRepository(@Qualifier("publicDataSource") DataSource publicDataSource) {
        this.jdbcTemplate = new JdbcTemplate(publicDataSource);
    }

    public List<WeeklyScheduleEntry> findAllByPhotographer(UUID photographerId) {
        return jdbcTemplate.query("""
                SELECT weekday, start_time, end_time
                FROM scheduling.weekly_schedule
                WHERE photographer_id = ?
                ORDER BY weekday ASC, start_time ASC
                """,
                MAPPER,
                photographerId);
    }
}
