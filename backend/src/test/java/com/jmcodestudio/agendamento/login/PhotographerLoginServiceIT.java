package com.jmcodestudio.agendamento.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jmcodestudio.agendamento.config.LoginDataSourceConfig;
import com.jmcodestudio.agendamento.config.PasswordEncoderConfig;
import com.jmcodestudio.agendamento.config.SignupDataSourceConfig;
import com.jmcodestudio.agendamento.login.exception.AccountSecurityBlockedException;
import com.jmcodestudio.agendamento.login.exception.InvalidCredentialsException;
import com.jmcodestudio.agendamento.login.exception.LoginRateLimitedException;
import com.jmcodestudio.agendamento.signup.PlatformSettingsService;

@SpringJUnitConfig
class PhotographerLoginServiceIT {

    private static final String SIGNUP_USER = "agendamento_signup";
    private static final String SIGNUP_PASSWORD = "signup_test_pw";
    private static final String LOGIN_USER = "agendamento_login";
    private static final String LOGIN_PASSWORD = "login_test_pw";
    private static final String HMAC_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private static final String EMAIL = "maria@example.com";
    private static final String NAME = "Maria Silva";
    private static final String PASSWORD = "Correct-Horse-Battery-42";
    private static final String CLIENT_IP = "203.0.113.10";

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
        // Passos manuais em produção: não fazem parte das migrations.
        admin.execute("CREATE ROLE " + SIGNUP_USER + " LOGIN PASSWORD '" + SIGNUP_PASSWORD + "'");
        admin.execute("GRANT jm_signup TO " + SIGNUP_USER);
        admin.execute("CREATE ROLE " + LOGIN_USER + " LOGIN PASSWORD '" + LOGIN_PASSWORD + "'");
        admin.execute("GRANT jm_login TO " + LOGIN_USER);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(5432));
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("DB_SIGNUP_USER", () -> SIGNUP_USER);
        registry.add("DB_SIGNUP_PASSWORD", () -> SIGNUP_PASSWORD);
        registry.add("DB_LOGIN_USER", () -> LOGIN_USER);
        registry.add("DB_LOGIN_PASSWORD", () -> LOGIN_PASSWORD);
        registry.add("ACCESS_ATTEMPTS_HMAC_KEY", () -> HMAC_KEY);
    }

    // PlatformSettingsService lê pelo signupDataSource, por isso os dois DataSources entram.
    @Configuration
    @EnableTransactionManagement
    @Import({ SignupDataSourceConfig.class, LoginDataSourceConfig.class, PasswordEncoderConfig.class,
            PlatformSettingsService.class, LoginCredentialRepository.class,
            AccessAttemptRepository.class, AccessAttemptHmac.class, PhotographerLoginService.class })
    static class TestConfig {

        // Necessário em contexto Spring puro (não @SpringBootTest) para resolver os
        // @Value("${...}") registrados via @DynamicPropertySource.
        @Bean
        static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Autowired
    private PhotographerLoginService service;

    @Autowired
    private AccessAttemptHmac hmac;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformSettingsService settings;

    private UUID photographerId;
    private UUID photographerUserId;

    @BeforeEach
    void resetData() {
        admin.execute("TRUNCATE scheduling.access_attempts, scheduling.subscriptions, "
                + "scheduling.photographer_users, scheduling.photographers, scheduling.admin_users CASCADE");
        createPhotographer(EMAIL);
    }

    private void createPhotographer(String email) {
        photographerId = UUID.randomUUID();
        photographerUserId = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.photographers
                    (id, name, email, email_normalized, public_slug, timezone,
                     account_status, public_page_enabled, push_enabled)
                VALUES (?, ?, ?, ?, ?, 'America/Sao_Paulo', 'EM_TESTE', true, false)
                """, photographerId, NAME, email, email, "slug-" + photographerId);
        admin.update("""
                INSERT INTO scheduling.photographer_users
                    (id, photographer_id, email, email_normalized, password_hash)
                VALUES (?, ?, ?, ?, ?)
                """, photographerUserId, photographerId, email, email, passwordEncoder.encode(PASSWORD));
    }

    private void blockPhotographer() {
        UUID adminId = UUID.randomUUID();
        admin.update("INSERT INTO scheduling.admin_users (id, email_normalized) VALUES (?, ?)",
                adminId, "admin@example.com");
        admin.update("""
                UPDATE scheduling.photographers
                SET security_blocked_at = now(), security_block_reason = 'teste', security_blocked_by = ?
                WHERE id = ?
                """, adminId, photographerId);
    }

    private void insertFailures(int amount, byte[] subjectKey, byte[] originKey) {
        for (int i = 0; i < amount; i++) {
            admin.update("""
                    INSERT INTO scheduling.access_attempts (id, scope, subject_key, origin_key, succeeded)
                    VALUES (?, 'LOGIN', ?, ?, false)
                    """, UUID.randomUUID(), subjectKey, originKey);
        }
    }

    private List<Map<String, Object>> attempts() {
        return admin.queryForList("SELECT photographer_id, succeeded FROM scheduling.access_attempts");
    }

    @Test
    void correctCredentialsReturnResultUpdateLastLoginAndRecordSuccess() {
        LoginResult result = service.login(" Maria@Example.com ", PASSWORD, CLIENT_IP);

        assertEquals(photographerId, result.photographerId());
        assertEquals(NAME, result.name());
        assertNotNull(admin.queryForObject(
                "SELECT last_login_at FROM scheduling.photographer_users WHERE id = ?",
                Object.class, photographerUserId));

        List<Map<String, Object>> rows = attempts();
        assertEquals(1, rows.size());
        assertEquals(true, rows.get(0).get("succeeded"));
        assertEquals(photographerId, rows.get(0).get("photographer_id"));
    }

    @Test
    void wrongPasswordThrowsInvalidCredentialsAndRecordsFailureWithPhotographerId() {
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(EMAIL, "senha-errada-qualquer-123", CLIENT_IP));

        List<Map<String, Object>> rows = attempts();
        assertEquals(1, rows.size());
        assertEquals(false, rows.get(0).get("succeeded"));
        assertEquals(photographerId, rows.get(0).get("photographer_id"));
    }

    @Test
    void unknownEmailThrowsInvalidCredentialsAndRecordsFailureWithNullPhotographerId() {
        assertThrows(InvalidCredentialsException.class,
                () -> service.login("ninguem@example.com", PASSWORD, CLIENT_IP));

        List<Map<String, Object>> rows = attempts();
        assertEquals(1, rows.size());
        assertEquals(false, rows.get(0).get("succeeded"));
        assertNull(rows.get(0).get("photographer_id"));
    }

    // Trava a ordem do serviço: bloqueio de segurança é checado antes da senha.
    @Test
    void securityBlockedAccountThrowsBlockedExceptionEvenWithCorrectPassword() {
        blockPhotographer();

        assertThrows(AccountSecurityBlockedException.class,
                () -> service.login(EMAIL, PASSWORD, CLIENT_IP));

        List<Map<String, Object>> rows = attempts();
        assertEquals(1, rows.size());
        assertEquals(false, rows.get(0).get("succeeded"));
        assertEquals(photographerId, rows.get(0).get("photographer_id"));
    }

    @Test
    void rateLimitPerAccountIsCheckedBeforePassword() {
        int limit = settings.getIntSetting("login_failures_per_account");
        insertFailures(limit, hmac.hmac(EMAIL), hmac.hmac("198.51.100.99"));

        assertThrows(LoginRateLimitedException.class,
                () -> service.login(EMAIL, PASSWORD, CLIENT_IP));
    }

    @Test
    void rateLimitPerIpAppliesAcrossDifferentAccounts() {
        int limit = settings.getIntSetting("login_attempts_per_ip");
        byte[] originKey = hmac.hmac(CLIENT_IP);
        for (int i = 0; i < limit; i++) {
            insertFailures(1, hmac.hmac("outro" + i + "@example.com"), originKey);
        }

        assertThrows(LoginRateLimitedException.class,
                () -> service.login(EMAIL, PASSWORD, CLIENT_IP));
    }
}
