package com.jmcodestudio.agendamento.publicpage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.jmcodestudio.agendamento.config.PublicDataSourceConfig;
import com.jmcodestudio.agendamento.publicpage.exception.FotografoNaoEncontradoException;
import com.jmcodestudio.agendamento.publicpage.exception.ServicoNaoDisponivelException;

@SpringJUnitConfig
class PublicCatalogServiceIT {

    private static final String PUBLIC_USER = "agendamento_public";
    private static final String PUBLIC_PASSWORD = "public_test_pw";

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
        // Passo manual em produção: o login público herda jm_public (NOBYPASSRLS), RLS vale de verdade.
        admin.execute("CREATE ROLE " + PUBLIC_USER + " LOGIN PASSWORD '" + PUBLIC_PASSWORD + "'");
        admin.execute("GRANT jm_public TO " + PUBLIC_USER);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(5432));
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("DB_PUBLIC_USER", () -> PUBLIC_USER);
        registry.add("DB_PUBLIC_PASSWORD", () -> PUBLIC_PASSWORD);
    }

    @Configuration
    @EnableTransactionManagement
    @Import({ PublicDataSourceConfig.class, PublicPhotographerRepository.class,
            PublicServiceRepository.class, PublicWeeklyScheduleRepository.class,
            PublicOccupancyRepository.class, PublicCatalogService.class })
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Autowired
    private PublicCatalogService service;

    private UUID photographerId;
    private String slug;
    private UUID serviceId;

    @BeforeEach
    void resetData() {
        admin.execute("TRUNCATE scheduling.photographers CASCADE");
        slug = "ana-" + UUID.randomUUID();
        photographerId = insertPhotographer("Ana Souza", slug, "EM_TESTE", true, null);
        serviceId = insertService(photographerId, "Ensaio externo", 60, 0, 0, false, "active");
        insertInactiveService(photographerId, "Ensaio descontinuado");
        insertFullWeekSchedule(photographerId, LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    @Test
    void unknownSlugThrowsGenericNotFound() {
        assertThrows(FotografoNaoEncontradoException.class, () -> service.findPhotographer("slug-que-nao-existe"));
    }

    @Test
    void knownSlugWithActiveAccountIsAvailable() {
        PublicPhotographerResponse response = service.findPhotographer(slug);

        assertEquals("Ana Souza", response.name());
        assertEquals("America/Sao_Paulo", response.timezone());
        assertTrue(response.available());
    }

    @Test
    void suspendedAccountIsNotAvailableButStillFound() {
        admin.update("UPDATE scheduling.photographers SET account_status = 'SUSPENSA', "
                + "suspended_at = now(), suspension_reason = 'TESTE' WHERE id = ?", photographerId);

        PublicPhotographerResponse response = service.findPhotographer(slug);

        assertFalse(response.available());
    }

    @Test
    void listServicesReturnsOnlyActiveOnesWithPriceSnapshotHidden() {
        List<PublicServiceResponse> services = service.listServices(slug);

        assertEquals(1, services.size());
        assertEquals("Ensaio externo", services.get(0).name());
        // pricePublic=false (ver insertService) -> priceAmount deve vir oculto (RN-004B).
        assertEquals(null, services.get(0).priceAmount());
    }

    @Test
    void listServicesOnSuspendedAccountThrowsGenericNotFound() {
        admin.update("UPDATE scheduling.photographers SET account_status = 'SUSPENSA', "
                + "suspended_at = now(), suspension_reason = 'TESTE' WHERE id = ?", photographerId);

        assertThrows(FotografoNaoEncontradoException.class, () -> service.listServices(slug));
    }

    @Test
    void availabilityOffersOneSlotPerServiceDurationAcrossTheWindow() {
        LocalDate date = nextDateFarInTheFuture();

        AvailabilityResponse response = service.availability(slug, serviceId, date);

        // 09:00-18:00, serviço de 60 min, sem ocupação -> 9 slots.
        assertEquals(9, response.slots().size());
        assertEquals(OffsetDateTime.of(date, LocalTime.of(9, 0), ZoneOffset.of("-03:00")), response.slots().get(0));
    }

    @Test
    void availabilityExcludesSlotOccupiedByACalendarBlock() {
        LocalDate date = nextDateFarInTheFuture();
        insertCalendarBlock(photographerId, date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        AvailabilityResponse response = service.availability(slug, serviceId, date);

        assertFalse(response.slots().contains(
                OffsetDateTime.of(date, LocalTime.of(10, 0), ZoneOffset.of("-03:00"))));
        assertEquals(8, response.slots().size());
    }

    @Test
    void availabilityForInactiveServiceThrowsGenericNotFound() {
        LocalDate date = nextDateFarInTheFuture();
        UUID inactiveId = admin.queryForObject(
                "SELECT id FROM scheduling.services WHERE photographer_id = ? AND active = false",
                UUID.class, photographerId);

        assertThrows(ServicoNaoDisponivelException.class, () -> service.availability(slug, inactiveId, date));
    }

    private UUID insertPhotographer(String name, String slug, String accountStatus,
            boolean publicPageEnabled, LocalDate horizon) {
        UUID id = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.photographers
                    (id, name, email, email_normalized, whatsapp, whatsapp_normalized,
                     public_slug, timezone, account_status, booking_horizon_date,
                     public_page_enabled, push_enabled)
                VALUES (?, ?, ?, ?, NULL, NULL, ?, 'America/Sao_Paulo', ?, ?, ?, false)
                """, id, name, slug + "@example.com", slug + "@example.com", slug,
                accountStatus, horizon, publicPageEnabled);
        return id;
    }

    private UUID insertService(UUID photographerId, String name, int durationMinutes,
            int minimumNoticeMinutes, int bufferAfterMinutes, boolean pricePublic, String label) {
        UUID id = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.services
                    (id, photographer_id, name, duration_minutes, price_amount, price_public,
                     minimum_notice_minutes, buffer_after_minutes, active)
                VALUES (?, ?, ?, ?, 150.00, ?, ?, ?, true)
                """, id, photographerId, name, durationMinutes, pricePublic,
                minimumNoticeMinutes, bufferAfterMinutes);
        return id;
    }

    private void insertInactiveService(UUID photographerId, String name) {
        admin.update("""
                INSERT INTO scheduling.services
                    (id, photographer_id, name, duration_minutes, price_public,
                     minimum_notice_minutes, buffer_after_minutes, active)
                VALUES (?, ?, ?, 60, false, 0, 0, false)
                """, UUID.randomUUID(), photographerId, name);
    }

    private void insertFullWeekSchedule(UUID photographerId, LocalTime start, LocalTime end) {
        for (int weekday = 0; weekday <= 6; weekday++) {
            admin.update("""
                    INSERT INTO scheduling.weekly_schedule (photographer_id, weekday, start_time, end_time)
                    VALUES (?, ?, ?, ?)
                    """, photographerId, weekday, start, end);
        }
    }

    private void insertCalendarBlock(UUID photographerId, LocalDate date, LocalTime start, LocalTime end) {
        admin.update("""
                INSERT INTO scheduling.calendar_blocks (id, photographer_id, block_type, starts_at, ends_at)
                VALUES (?, ?, 'MANUAL_BLOCK', ?, ?)
                """, UUID.randomUUID(), photographerId,
                OffsetDateTime.of(date, start, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(date, end, ZoneOffset.of("-03:00")));
    }

    /** Data distante o suficiente pra nunca colidir com antecedência mínima real. */
    private static LocalDate nextDateFarInTheFuture() {
        LocalDate date = LocalDate.now().plusYears(1);
        // Convenção 0=domingo..6=sábado; qualquer dia serve, já que a jornada cobre a semana toda.
        return date;
    }
}
