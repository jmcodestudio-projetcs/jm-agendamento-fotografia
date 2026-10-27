package com.jmcodestudio.agendamento.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jmcodestudio.agendamento.config.TenantDataSourceConfig;
import com.jmcodestudio.agendamento.profile.exception.InvalidWhatsappException;
import com.jmcodestudio.agendamento.signup.exception.TimezoneInvalidaException;

@SpringJUnitConfig
class PhotographerProfileServiceIT {

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
    @Import({ TenantDataSourceConfig.class, PhotographerProfileRepository.class,
            PhotographerProfileService.class })
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Autowired
    private PhotographerProfileService service;

    @Autowired
    private PhotographerProfileRepository repository;

    @Autowired
    @Qualifier("tenantTransactionManager")
    private PlatformTransactionManager tenantTransactionManager;

    private UUID photographerA;
    private UUID photographerB;

    @BeforeEach
    void resetData() {
        admin.execute("TRUNCATE scheduling.photographers CASCADE");
        photographerA = insertPhotographer("Ana Souza", "ana@example.com", "(21) 91111-2222",
                "5521911112222", "America/Sao_Paulo");
        photographerB = insertPhotographer("Bruno Lima", "bruno@example.com", "(31) 93333-4444",
                "5531933334444", "America/Manaus");
    }

    private UUID insertPhotographer(String name, String email, String whatsapp,
            String whatsappNormalized, String timezone) {
        UUID id = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.photographers
                    (id, name, email, email_normalized, whatsapp, whatsapp_normalized,
                     public_slug, timezone, account_status, public_page_enabled, push_enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'EM_TESTE', true, false)
                """, id, name, email, email, whatsapp, whatsappNormalized, "slug-" + id, timezone);
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return admin.queryForMap("""
                SELECT name, whatsapp, whatsapp_normalized, timezone
                FROM scheduling.photographers WHERE id = ?
                """, id);
    }

    @Test
    void getProfileReturnsOnlyTheRequestedPhotographersData() {
        ProfileResponse response = service.getProfile(photographerA);

        assertEquals("Ana Souza", response.name());
        assertEquals("ana@example.com", response.email());
        assertEquals("(21) 91111-2222", response.whatsapp());
        assertEquals("America/Sao_Paulo", response.timezone());
    }

    @Test
    void updateProfileStoresTrimmedValuesAndNormalizedWhatsapp() {
        service.updateProfile(photographerA,
                new ProfileUpdateRequest("  Ana Maria  ", " (11) 98765-4321 ", "America/Recife"));

        ProfileResponse updated = service.getProfile(photographerA);
        assertEquals("Ana Maria", updated.name());
        assertEquals("(11) 98765-4321", updated.whatsapp());
        assertEquals("America/Recife", updated.timezone());
        assertEquals("5511987654321", row(photographerA).get("whatsapp_normalized"));
    }

    @Test
    void updateProfileDoesNotTouchOtherPhotographers() {
        service.updateProfile(photographerA,
                new ProfileUpdateRequest("Ana Maria", "11987654321", "America/Recife"));

        Map<String, Object> other = row(photographerB);
        assertEquals("Bruno Lima", other.get("name"));
        assertEquals("(31) 93333-4444", other.get("whatsapp"));
        assertEquals("5531933334444", other.get("whatsapp_normalized"));
        assertEquals("America/Manaus", other.get("timezone"));
    }

    @Test
    void updateProfileWithBlankOrNullWhatsappClearsBothColumns() {
        service.updateProfile(photographerA, new ProfileUpdateRequest("Ana Souza", "   ", "America/Sao_Paulo"));

        Map<String, Object> afterBlank = row(photographerA);
        assertNull(afterBlank.get("whatsapp"));
        assertNull(afterBlank.get("whatsapp_normalized"));

        service.updateProfile(photographerB, new ProfileUpdateRequest("Bruno Lima", null, "America/Manaus"));

        Map<String, Object> afterNull = row(photographerB);
        assertNull(afterNull.get("whatsapp"));
        assertNull(afterNull.get("whatsapp_normalized"));
    }

    @Test
    void updateProfileWithInvalidTimezoneThrowsAndLeavesDataUnchanged() {
        assertThrows(TimezoneInvalidaException.class, () -> service.updateProfile(photographerA,
                new ProfileUpdateRequest("Novo Nome", "11987654321", "Nao/Existe")));

        Map<String, Object> unchanged = row(photographerA);
        assertEquals("Ana Souza", unchanged.get("name"));
        assertEquals("(21) 91111-2222", unchanged.get("whatsapp"));
        assertEquals("America/Sao_Paulo", unchanged.get("timezone"));
    }

    @Test
    void updateProfileWithUnnormalizableWhatsappThrowsAndLeavesDataUnchanged() {
        assertThrows(InvalidWhatsappException.class, () -> service.updateProfile(photographerA,
                new ProfileUpdateRequest("Novo Nome", "123", "America/Sao_Paulo")));

        Map<String, Object> unchanged = row(photographerA);
        assertEquals("Ana Souza", unchanged.get("name"));
        assertEquals("(21) 91111-2222", unchanged.get("whatsapp"));
    }

    @Test
    void tenantContextOfOnePhotographerCannotReadOrUpdateAnother() {
        TransactionTemplate tx = new TransactionTemplate(tenantTransactionManager);

        // Contexto de A: enxerga A, mas B é invisível mesmo pedindo o id de B explicitamente.
        Optional<PhotographerProfile> ownProfile = tx.execute(status -> {
            repository.setTenantContext(photographerA);
            return repository.findById(photographerA);
        });
        assertTrue(ownProfile.isPresent());
        assertEquals("Ana Souza", ownProfile.get().name());

        Optional<PhotographerProfile> foreignProfile = tx.execute(status -> {
            repository.setTenantContext(photographerA);
            return repository.findById(photographerB);
        });
        assertTrue(foreignProfile.isEmpty());

        // UPDATE com o id de B sob o contexto de A não pode alterar nada.
        tx.executeWithoutResult(status -> {
            repository.setTenantContext(photographerA);
            repository.update(photographerB, "Invasor", "11999999999", "5511999999999", "UTC");
        });

        Map<String, Object> untouched = row(photographerB);
        assertEquals("Bruno Lima", untouched.get("name"));
        assertEquals("(31) 93333-4444", untouched.get("whatsapp"));
        assertEquals("5531933334444", untouched.get("whatsapp_normalized"));
        assertEquals("America/Manaus", untouched.get("timezone"));
    }

    @Test
    void withoutTenantContextNothingIsVisible() {
        TransactionTemplate tx = new TransactionTemplate(tenantTransactionManager);

        Optional<PhotographerProfile> profile = tx.execute(status -> repository.findById(photographerA));

        assertTrue(profile.isEmpty());
    }
}
