package com.jmcodestudio.agendamento.agenda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jmcodestudio.agendamento.agenda.exception.BloqueioConflitanteException;
import com.jmcodestudio.agendamento.agenda.exception.BloqueioNaoEncontradoException;
import com.jmcodestudio.agendamento.agenda.exception.FaixaInvalidaException;
import com.jmcodestudio.agendamento.config.TenantDataSourceConfig;

@SpringJUnitConfig
class AgendaSettingsServiceIT {

    private static final String TENANT_USER = "agendamento_tenant";
    private static final String TENANT_PASSWORD = "tenant_test_pw";

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("agendamento_fotografia");

    private static JdbcTemplate admin;

    static {
        POSTGRES.start();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("scheduling", "authentication", "public")
                .defaultSchema("public")
                .createSchemas(false)
                .load()
                .migrate();

        admin = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        // Passo manual em produção: o login do tenant herda jm_tenant (NOBYPASSRLS), então a RLS vale de verdade.
        admin.execute("CREATE ROLE " + TENANT_USER + " LOGIN PASSWORD '" + TENANT_PASSWORD + "'");
        admin.execute("GRANT jm_tenant TO " + TENANT_USER);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(5432));
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("DB_TENANT_USER", () -> TENANT_USER);
        registry.add("DB_TENANT_PASSWORD", () -> TENANT_PASSWORD);
    }

    @Configuration
    @EnableTransactionManagement
    @Import({ TenantDataSourceConfig.class, WeeklyScheduleRepository.class,
            CalendarBlockRepository.class, BookingHorizonRepository.class,
            AgendaSettingsService.class })
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Autowired
    private AgendaSettingsService service;

    private UUID photographerA;
    private UUID photographerB;

    @BeforeEach
    void resetData() {
        admin.execute("TRUNCATE scheduling.photographers CASCADE");
        photographerA = insertPhotographer("Ana Souza", "ana@example.com");
        photographerB = insertPhotographer("Bruno Lima", "bruno@example.com");
    }

    private UUID insertPhotographer(String name, String email) {
        UUID id = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.photographers
                    (id, name, email, email_normalized, whatsapp, whatsapp_normalized,
                     public_slug, timezone, account_status, public_page_enabled, push_enabled)
                VALUES (?, ?, ?, ?, NULL, NULL, ?, 'America/Sao_Paulo', 'EM_TESTE', true, false)
                """, id, name, email, email, "slug-" + id);
        return id;
    }

    private static WeeklyScheduleRequest request(WeeklyScheduleRangeRequest... ranges) {
        return new WeeklyScheduleRequest(List.of(ranges));
    }

    @Test
    void settingsStartEmptyWithTimezoneAndNoHorizon() {
        AgendaSettingsResponse settings = service.getSettings(photographerA);

        assertEquals("America/Sao_Paulo", settings.timezone());
        assertNull(settings.bookingHorizonDate());
        assertTrue(settings.weeklySchedule().isEmpty());
        assertTrue(settings.calendarBlocks().isEmpty());
    }

    @Test
    void replaceWeeklyScheduleWithOrdinaryRangePersists() {
        List<WeeklyScheduleEntry> result = service.replaceWeeklySchedule(photographerA,
                request(new WeeklyScheduleRangeRequest(1, LocalTime.of(9, 0), LocalTime.of(18, 0))));

        assertEquals(List.of(new WeeklyScheduleEntry(1, LocalTime.of(9, 0), LocalTime.of(18, 0))), result);
    }

    @Test
    void replaceWeeklyScheduleSplitsRangeThatCrossesMidnight() {
        // Sexta (5) 22:00 até Sábado (6) 02:00.
        List<WeeklyScheduleEntry> result = service.replaceWeeklySchedule(photographerA,
                request(new WeeklyScheduleRangeRequest(5, LocalTime.of(22, 0), LocalTime.of(2, 0))));

        assertEquals(List.of(
                new WeeklyScheduleEntry(5, LocalTime.of(22, 0), LocalTime.of(23, 59, 59)),
                new WeeklyScheduleEntry(6, LocalTime.MIDNIGHT, LocalTime.of(2, 0))),
                result);
    }

    @Test
    void replaceWeeklyScheduleWithRangeEndingAtMidnightDoesNotCrossToNextDay() {
        // Regressão: 18:00 -> 00:00 é "até o fim do dia", não deve gerar uma
        // segunda faixa (dia+1, 00:00, 00:00), que violaria o CHECK do banco.
        List<WeeklyScheduleEntry> result = service.replaceWeeklySchedule(photographerA,
                request(new WeeklyScheduleRangeRequest(1, LocalTime.of(18, 0), LocalTime.MIDNIGHT)));

        assertEquals(List.of(new WeeklyScheduleEntry(1, LocalTime.of(18, 0), LocalTime.of(23, 59, 59))), result);
    }

    @Test
    void replaceWeeklyScheduleWithOverlappingRangesThrowsAndKeepsPreviousSchedule() {
        service.replaceWeeklySchedule(photographerA,
                request(new WeeklyScheduleRangeRequest(2, LocalTime.of(9, 0), LocalTime.of(12, 0))));

        assertThrows(FaixaInvalidaException.class, () -> service.replaceWeeklySchedule(photographerA,
                request(
                        new WeeklyScheduleRangeRequest(3, LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        new WeeklyScheduleRangeRequest(3, LocalTime.of(11, 0), LocalTime.of(14, 0)))));

        assertEquals(List.of(new WeeklyScheduleEntry(2, LocalTime.of(9, 0), LocalTime.of(12, 0))),
                service.getSettings(photographerA).weeklySchedule());
    }

    @Test
    void weeklySchedulesAreIsolatedByPhotographer() {
        service.replaceWeeklySchedule(photographerA,
                request(new WeeklyScheduleRangeRequest(1, LocalTime.of(8, 0), LocalTime.of(12, 0))));
        service.replaceWeeklySchedule(photographerB,
                request(new WeeklyScheduleRangeRequest(2, LocalTime.of(13, 0), LocalTime.of(17, 0))));

        assertEquals(1, service.getSettings(photographerA).weeklySchedule().get(0).weekday());
        assertEquals(2, service.getSettings(photographerB).weeklySchedule().get(0).weekday());
    }

    @Test
    void createBlockSucceedsAndAppearsInList() {
        OffsetDateTime start = OffsetDateTime.of(2026, 11, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 11, 10, 11, 0, 0, 0, ZoneOffset.UTC);

        CalendarBlock created = service.createBlock(photographerA,
                new CalendarBlockCreateRequest(BlockType.TIME_OFF, start, end, "Viagem"));

        assertEquals("Viagem", created.reason());
        assertEquals(List.of(created), service.listBlocks(photographerA));
    }

    @Test
    void createBlockOverlappingAnotherBlockThrowsConflictAndDoesNotPersist() {
        OffsetDateTime start = OffsetDateTime.of(2026, 11, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 11, 10, 11, 0, 0, 0, ZoneOffset.UTC);
        service.createBlock(photographerA, new CalendarBlockCreateRequest(BlockType.TIME_OFF, start, end, "Viagem"));

        OffsetDateTime overlappingStart = OffsetDateTime.of(2026, 11, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime overlappingEnd = OffsetDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneOffset.UTC);

        assertThrows(BloqueioConflitanteException.class, () -> service.createBlock(photographerA,
                new CalendarBlockCreateRequest(BlockType.MANUAL_BLOCK, overlappingStart, overlappingEnd, null)));

        assertEquals(1, service.listBlocks(photographerA).size());
    }

    @Test
    void deleteBlockRemovesItAndOfAnotherPhotographerThrows() {
        OffsetDateTime start = OffsetDateTime.of(2026, 11, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 11, 10, 11, 0, 0, 0, ZoneOffset.UTC);
        CalendarBlock created = service.createBlock(photographerA,
                new CalendarBlockCreateRequest(BlockType.TIME_OFF, start, end, null));

        assertThrows(BloqueioNaoEncontradoException.class,
                () -> service.deleteBlock(photographerB, created.id()));
        assertEquals(1, service.listBlocks(photographerA).size());

        service.deleteBlock(photographerA, created.id());
        assertTrue(service.listBlocks(photographerA).isEmpty());
    }

    @Test
    void updateBookingHorizonSetsAndClearsDate() {
        LocalDate horizon = LocalDate.of(2027, 3, 1);

        service.updateBookingHorizon(photographerA, new BookingHorizonRequest(horizon));
        assertEquals(horizon, service.getSettings(photographerA).bookingHorizonDate());
        assertNull(service.getSettings(photographerB).bookingHorizonDate());

        service.updateBookingHorizon(photographerA, new BookingHorizonRequest(null));
        assertNull(service.getSettings(photographerA).bookingHorizonDate());
    }

    @Test
    void blocksAreIsolatedByPhotographer() {
        OffsetDateTime start = OffsetDateTime.of(2026, 11, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 11, 10, 11, 0, 0, 0, ZoneOffset.UTC);
        service.createBlock(photographerA, new CalendarBlockCreateRequest(BlockType.TIME_OFF, start, end, null));

        assertTrue(service.listBlocks(photographerB).isEmpty());
    }
}
