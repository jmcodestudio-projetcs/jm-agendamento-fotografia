package com.jmcodestudio.agendamento.signup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import com.jmcodestudio.agendamento.config.PasswordEncoderConfig;
import com.jmcodestudio.agendamento.config.SignupDataSourceConfig;
import com.jmcodestudio.agendamento.signup.exception.EmailJaCadastradoException;
import com.jmcodestudio.agendamento.signup.exception.InvalidPasswordException;
import com.jmcodestudio.agendamento.signup.exception.TimezoneInvalidaException;

@SpringJUnitConfig
class PhotographerSignupServiceIT {

    private static final String SIGNUP_USER = "agendamento_signup";
    private static final String SIGNUP_PASSWORD = "signup_test_pw";
    private static final String VALID_PASSWORD = "Correct-Horse-Battery-42";
    private static final String TIMEZONE = "America/Sao_Paulo";

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
        // Passo manual em produção: não faz parte da migration.
        admin.execute("CREATE ROLE " + SIGNUP_USER + " LOGIN PASSWORD '" + SIGNUP_PASSWORD + "'");
        admin.execute("GRANT jm_signup TO " + SIGNUP_USER);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(5432));
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("DB_SIGNUP_USER", () -> SIGNUP_USER);
        registry.add("DB_SIGNUP_PASSWORD", () -> SIGNUP_PASSWORD);
    }

    @Configuration
    @EnableTransactionManagement
    @Import({ SignupDataSourceConfig.class, PasswordEncoderConfig.class,
            PhotographerSignupRepository.class, PlatformSettingsService.class,
            CommonPasswordChecker.class, PhotographerSignupService.class })
    static class TestConfig {

        // Sem isto, @Value("${DB_HOST:...}") em SignupDataSourceConfig nao e
        // resolvido neste contexto Spring puro (@SpringJUnitConfig, nao
        // @SpringBootTest) - o Hikari receberia a string literal do placeholder
        // em vez do valor real registrado por @DynamicPropertySource. Bean
        // estatico: precisa rodar como BeanFactoryPostProcessor antes dos
        // outros beans serem instanciados.
        @Bean
        static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Autowired
    private PhotographerSignupService service;

    @BeforeEach
    void cleanTables() {
        admin.execute("TRUNCATE scheduling.subscriptions, scheduling.photographer_users, "
                + "scheduling.photographers CASCADE");
    }

    private static SignupRequest request(String name, String email) {
        return new SignupRequest(name, email, VALID_PASSWORD, TIMEZONE);
    }

    private static int count(String table) {
        return admin.queryForObject("SELECT count(*) FROM scheduling." + table, Integer.class);
    }

    @Test
    void validSignupCreatesThreeRowsAndReturnsUuid() {
        UUID id = service.register(request("Maria Silva", "Maria@Example.com "));

        assertNotNull(id);
        assertEquals(1, count("photographers"));
        assertEquals(1, count("photographer_users"));
        assertEquals(1, count("subscriptions"));
        assertEquals("maria-silva", admin.queryForObject(
                "SELECT public_slug FROM scheduling.photographers WHERE id = ?", String.class, id));
        assertEquals("maria@example.com", admin.queryForObject(
                "SELECT email_normalized FROM scheduling.photographer_users WHERE photographer_id = ?",
                String.class, id));
        assertEquals("TRIAL", admin.queryForObject(
                "SELECT status FROM scheduling.subscriptions WHERE photographer_id = ?", String.class, id));
    }

    @Test
    void duplicateEmailThrowsEmailJaCadastrado() {
        service.register(request("Maria Silva", "maria@example.com"));

        assertThrows(EmailJaCadastradoException.class,
                () -> service.register(request("Outra Pessoa", " MARIA@example.com")));
        assertEquals(1, count("photographers"));
    }

    @Test
    void concurrentSignupsWithSameSlugBaseGetSuffixInsteadOfError() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<UUID>> tasks = new ArrayList<>();
            for (String email : List.of("a@example.com", "b@example.com")) {
                tasks.add(() -> {
                    start.await();
                    return service.register(request("Ana Souza", email));
                });
            }
            List<Future<UUID>> futures = new ArrayList<>();
            for (Callable<UUID> task : tasks) {
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (Future<UUID> future : futures) {
                assertNotNull(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        Set<String> slugs = Set.copyOf(admin.queryForList(
                "SELECT public_slug FROM scheduling.photographers", String.class));
        assertEquals(Set.of("ana-souza", "ana-souza-2"), slugs);
        assertEquals(2, count("photographer_users"));
        assertEquals(2, count("subscriptions"));
    }

    @Test
    void passwordOutsideConfiguredLengthThrowsInvalidPassword() {
        int min = admin.queryForObject(
                "SELECT integer_value FROM scheduling.platform_settings WHERE setting_key = 'password_min_chars'",
                Integer.class);
        int max = admin.queryForObject(
                "SELECT integer_value FROM scheduling.platform_settings WHERE setting_key = 'password_max_chars'",
                Integer.class);

        assertThrows(InvalidPasswordException.class, () -> service.register(
                new SignupRequest("Maria", "m1@example.com", "x".repeat(min - 1), TIMEZONE)));
        assertThrows(InvalidPasswordException.class, () -> service.register(
                new SignupRequest("Maria", "m2@example.com", "x".repeat(max + 1), TIMEZONE)));
        assertEquals(0, count("photographers"));
    }

    @Test
    void invalidTimezoneThrowsTimezoneInvalida() {
        assertThrows(TimezoneInvalidaException.class, () -> service.register(
                new SignupRequest("Maria", "m@example.com", VALID_PASSWORD, "Mars/Olympus")));
        assertTrue(count("photographers") == 0);
    }
}
