package com.jmcodestudio.agendamento.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

import com.jmcodestudio.agendamento.catalog.exception.PrecoPublicoSemValorException;
import com.jmcodestudio.agendamento.catalog.exception.ServicoNaoEncontradoException;
import com.jmcodestudio.agendamento.config.TenantDataSourceConfig;

@SpringJUnitConfig
class ServiceOfferingServiceIT {

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
    @Import({ TenantDataSourceConfig.class, ServiceOfferingRepository.class,
            ServiceOfferingService.class })
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Autowired
    private ServiceOfferingService service;

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

    private static ServiceCreateRequest createRequest(String name, BigDecimal price, boolean pricePublic) {
        return new ServiceCreateRequest(name, "Descrição", 60, price, pricePublic, 120, 30);
    }

    private boolean activeInDb(UUID serviceId) {
        return Boolean.TRUE.equals(admin.queryForObject(
                "SELECT active FROM scheduling.services WHERE id = ?", Boolean.class, serviceId));
    }

    @Test
    void createWithPublicPriceReturnsCorrectFieldsAndIsActive() {
        ServiceResponse created = service.create(photographerA,
                createRequest("  Ensaio Gestante  ", new BigDecimal("350.00"), true));

        assertNotNull(created.id());
        assertEquals("Ensaio Gestante", created.name());
        assertEquals("Descrição", created.description());
        assertEquals(60, created.durationMinutes());
        assertEquals(0, new BigDecimal("350.00").compareTo(created.priceAmount()));
        assertTrue(created.pricePublic());
        assertEquals(120, created.minimumNoticeMinutes());
        assertEquals(30, created.bufferAfterMinutes());
        assertTrue(created.active());
    }

    @Test
    void createWithoutPriceWorks() {
        ServiceResponse created = service.create(photographerA, createRequest("Mini ensaio", null, false));

        assertNull(created.priceAmount());
        assertFalse(created.pricePublic());
        assertTrue(created.active());
    }

    @Test
    void createWithPublicPriceButNoAmountThrowsAndStoresNothing() {
        assertThrows(PrecoPublicoSemValorException.class,
                () -> service.create(photographerA, createRequest("Sem valor", null, true)));

        assertTrue(service.list(photographerA).isEmpty());
    }

    @Test
    void listReturnsOnlyOwnServicesOrderedByName() {
        service.create(photographerA, createRequest("Casamento", null, false));
        service.create(photographerA, createRequest("Aniversário", null, false));
        service.create(photographerA, createRequest("Batizado", null, false));
        service.create(photographerB, createRequest("Abacaxi", null, false));

        List<String> names = service.list(photographerA).stream().map(ServiceResponse::name).toList();

        assertEquals(List.of("Aniversário", "Batizado", "Casamento"), names);
    }

    @Test
    void updateChangesFieldsAndKeepsActiveUnchanged() {
        ServiceResponse created = service.create(photographerA,
                createRequest("Ensaio", new BigDecimal("100.00"), true));
        service.updateActive(photographerA, created.id(), new ActiveStatusRequest(false));

        ServiceResponse updated = service.update(photographerA, created.id(),
                new ServiceUpdateRequest("  Ensaio Premium ", null, 90, null, false, 60, 15));

        assertEquals(created.id(), updated.id());
        assertEquals("Ensaio Premium", updated.name());
        assertNull(updated.description());
        assertEquals(90, updated.durationMinutes());
        assertNull(updated.priceAmount());
        assertFalse(updated.pricePublic());
        assertEquals(60, updated.minimumNoticeMinutes());
        assertEquals(15, updated.bufferAfterMinutes());
        assertFalse(updated.active());
        assertFalse(activeInDb(created.id()));
        assertEquals("Ensaio Premium",
                admin.queryForObject("SELECT name FROM scheduling.services WHERE id = ?",
                        String.class, created.id()));
    }

    @Test
    void updateOfAnotherPhotographersServiceThrowsAndLeavesItUnchanged() {
        ServiceResponse created = service.create(photographerA, createRequest("Ensaio", null, false));

        assertThrows(ServicoNaoEncontradoException.class, () -> service.update(photographerB, created.id(),
                new ServiceUpdateRequest("Invasor", null, 30, null, false, 0, 0)));

        assertEquals("Ensaio",
                admin.queryForObject("SELECT name FROM scheduling.services WHERE id = ?",
                        String.class, created.id()));
    }

    @Test
    void updateActiveWorksInBothDirections() {
        ServiceResponse created = service.create(photographerA, createRequest("Ensaio", null, false));

        ServiceResponse deactivated = service.updateActive(photographerA, created.id(),
                new ActiveStatusRequest(false));
        assertFalse(deactivated.active());
        assertFalse(activeInDb(created.id()));

        ServiceResponse reactivated = service.updateActive(photographerA, created.id(),
                new ActiveStatusRequest(true));
        assertTrue(reactivated.active());
        assertTrue(activeInDb(created.id()));
    }

    @Test
    void updateActiveOfAnotherPhotographersServiceThrows() {
        ServiceResponse created = service.create(photographerA, createRequest("Ensaio", null, false));

        assertThrows(ServicoNaoEncontradoException.class,
                () -> service.updateActive(photographerB, created.id(), new ActiveStatusRequest(false)));

        assertTrue(activeInDb(created.id()));
    }

    @Test
    void photographersNeverSeeEachOthersServices() {
        ServiceResponse serviceA = service.create(photographerA, createRequest("Serviço da Ana", null, false));
        ServiceResponse serviceB = service.create(photographerB, createRequest("Serviço do Bruno", null, false));

        List<ServiceResponse> listA = service.list(photographerA);
        List<ServiceResponse> listB = service.list(photographerB);

        assertEquals(List.of(serviceA.id()), listA.stream().map(ServiceResponse::id).toList());
        assertEquals(List.of(serviceB.id()), listB.stream().map(ServiceResponse::id).toList());
    }
}
