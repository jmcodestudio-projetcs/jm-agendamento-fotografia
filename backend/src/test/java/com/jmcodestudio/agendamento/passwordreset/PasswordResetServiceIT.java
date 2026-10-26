package com.jmcodestudio.agendamento.passwordreset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jmcodestudio.agendamento.config.PasswordEncoderConfig;
import com.jmcodestudio.agendamento.config.PasswordResetDataSourceConfig;
import com.jmcodestudio.agendamento.config.SignupDataSourceConfig;
import com.jmcodestudio.agendamento.login.AccessAttemptHmac;
import com.jmcodestudio.agendamento.passwordreset.exception.InvalidOrExpiredResetTokenException;
import com.jmcodestudio.agendamento.signup.PlatformSettingsService;

@SpringJUnitConfig
class PasswordResetServiceIT {

    private static final String SIGNUP_USER = "agendamento_signup";
    private static final String SIGNUP_PASSWORD = "signup_test_pw";
    private static final String RESET_USER = "agendamento_reset";
    private static final String RESET_PASSWORD = "reset_test_pw";
    private static final String HMAC_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String ENCRYPTION_KEY =
            Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private static final String EMAIL = "maria@example.com";
    private static final String PASSWORD = "Correct-Horse-Battery-42";
    private static final String NEW_PASSWORD = "Another-Long-Passphrase-99";
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
        admin.execute("CREATE ROLE " + RESET_USER + " LOGIN PASSWORD '" + RESET_PASSWORD + "'");
        admin.execute("GRANT jm_reset TO " + RESET_USER);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(5432));
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("DB_SIGNUP_USER", () -> SIGNUP_USER);
        registry.add("DB_SIGNUP_PASSWORD", () -> SIGNUP_PASSWORD);
        registry.add("DB_RESET_USER", () -> RESET_USER);
        registry.add("DB_RESET_PASSWORD", () -> RESET_PASSWORD);
        registry.add("ACCESS_ATTEMPTS_HMAC_KEY", () -> HMAC_KEY);
        registry.add("AUTH_MESSAGE_ENCRYPTION_KEY", () -> ENCRYPTION_KEY);
        registry.add("FRONTEND_BASE_URL", () -> "http://localhost:5173");
    }

    // PlatformSettingsService lê pelo signupDataSource, por isso os dois DataSources entram.
    @Configuration
    @EnableTransactionManagement
    @Import({ SignupDataSourceConfig.class, PasswordResetDataSourceConfig.class,
            PasswordEncoderConfig.class, PlatformSettingsService.class,
            PasswordResetCredentialRepository.class, PasswordResetTokenRepository.class,
            PasswordResetAttemptRepository.class, PasswordResetMessageWriter.class,
            AccessAttemptHmac.class, AuthMessageEncryption.class,
            SessionInvalidationService.class, PasswordResetService.class })
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        // Spring Session JDBC não faz parte do escopo deste teste (só o contrato de busca/exclusão).
        @Bean
        @SuppressWarnings("unchecked")
        FindByIndexNameSessionRepository<Session> sessionRepository() {
            return mock(FindByIndexNameSessionRepository.class);
        }
    }

    @Autowired
    private PasswordResetService service;

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
        admin.execute("TRUNCATE scheduling.authentication_messages, scheduling.password_reset_tokens, "
                + "scheduling.access_attempts, scheduling.subscriptions, "
                + "scheduling.photographer_users, scheduling.photographers CASCADE");
        photographerId = UUID.randomUUID();
        photographerUserId = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.photographers
                    (id, name, email, email_normalized, public_slug, timezone,
                     account_status, public_page_enabled, push_enabled)
                VALUES (?, 'Maria Silva', ?, ?, ?, 'America/Sao_Paulo', 'EM_TESTE', true, false)
                """, photographerId, EMAIL, EMAIL, "slug-" + photographerId);
        admin.update("""
                INSERT INTO scheduling.photographer_users
                    (id, photographer_id, email, email_normalized, password_hash)
                VALUES (?, ?, ?, ?, ?)
                """, photographerUserId, photographerId, EMAIL, EMAIL, passwordEncoder.encode(PASSWORD));
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] newRawToken() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return raw;
    }

    private static String encode(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private UUID insertToken(byte[] rawToken, Instant createdAt, Instant expiresAt) throws Exception {
        UUID id = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.password_reset_tokens
                    (id, photographer_user_id, token_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, photographerUserId, sha256(rawToken),
                Timestamp.from(createdAt), Timestamp.from(expiresAt));
        return id;
    }

    private UUID insertActiveToken(byte[] rawToken) throws Exception {
        return insertToken(rawToken, Instant.now().minusSeconds(60), Instant.now().plusSeconds(1800));
    }

    private int count(String table) {
        return admin.queryForObject("SELECT count(*) FROM scheduling." + table, Integer.class);
    }

    private Object tokenColumn(UUID tokenId, String column) {
        return admin.queryForObject(
                "SELECT " + column + " FROM scheduling.password_reset_tokens WHERE id = ?",
                Object.class, tokenId);
    }

    @Test
    void requestResetForExistingEmailCreatesTokenAndPendingMessage() {
        service.requestReset(" Maria@Example.com ", CLIENT_IP);

        List<Map<String, Object>> tokens = admin.queryForList(
                "SELECT id, consumed_at, revoked_at, expires_at > now() AS in_future "
                        + "FROM scheduling.password_reset_tokens WHERE photographer_user_id = ?",
                photographerUserId);
        assertEquals(1, tokens.size());
        assertNull(tokens.get(0).get("consumed_at"));
        assertNull(tokens.get(0).get("revoked_at"));
        assertEquals(true, tokens.get(0).get("in_future"));

        List<Map<String, Object>> messages = admin.queryForList(
                "SELECT status, payload_ciphertext, password_reset_token_id, recipient "
                        + "FROM scheduling.authentication_messages");
        assertEquals(1, messages.size());
        assertEquals("PENDING", messages.get(0).get("status"));
        assertNotNull(messages.get(0).get("payload_ciphertext"));
        assertEquals(tokens.get(0).get("id"), messages.get(0).get("password_reset_token_id"));
        assertEquals(EMAIL, messages.get(0).get("recipient"));
    }

    @Test
    void requestResetForUnknownEmailDoesNotThrowAndOnlyRecordsFailedAttempt() {
        assertDoesNotThrow(() -> service.requestReset("ninguem@example.com", CLIENT_IP));

        assertEquals(0, count("password_reset_tokens"));
        assertEquals(0, count("authentication_messages"));
        List<Map<String, Object>> attempts = admin.queryForList(
                "SELECT photographer_id, succeeded, scope FROM scheduling.access_attempts");
        assertEquals(1, attempts.size());
        assertEquals(false, attempts.get(0).get("succeeded"));
        assertNull(attempts.get(0).get("photographer_id"));
        assertEquals("RESET", attempts.get(0).get("scope"));
    }

    @Test
    void requestResetOverPerAccountLimitCreatesNoToken() {
        int limit = settings.getIntSetting("reset_per_account_hour");
        byte[] subjectKey = hmac.hmac(EMAIL);
        for (int i = 0; i < limit; i++) {
            admin.update("""
                    INSERT INTO scheduling.access_attempts (id, scope, subject_key, origin_key, succeeded)
                    VALUES (?, 'RESET', ?, ?, true)
                    """, UUID.randomUUID(), subjectKey, hmac.hmac("198.51.100." + i));
        }

        assertDoesNotThrow(() -> service.requestReset(EMAIL, CLIENT_IP));

        assertEquals(0, count("password_reset_tokens"));
        assertEquals(0, count("authentication_messages"));
    }

    @Test
    void confirmResetWithValidTokenChangesPasswordAndConsumesToken() throws Exception {
        String oldHash = admin.queryForObject(
                "SELECT password_hash FROM scheduling.photographer_users WHERE id = ?",
                String.class, photographerUserId);
        byte[] raw = newRawToken();
        UUID tokenId = insertActiveToken(raw);

        service.confirmReset(encode(raw), NEW_PASSWORD);

        String newHash = admin.queryForObject(
                "SELECT password_hash FROM scheduling.photographer_users WHERE id = ?",
                String.class, photographerUserId);
        assertNotEquals(oldHash, newHash);
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, newHash));
        assertNotNull(tokenColumn(tokenId, "consumed_at"));
    }

    @Test
    void confirmResetWithAlreadyConsumedTokenThrows() throws Exception {
        byte[] raw = newRawToken();
        insertActiveToken(raw);

        service.confirmReset(encode(raw), NEW_PASSWORD);

        assertThrows(InvalidOrExpiredResetTokenException.class,
                () -> service.confirmReset(encode(raw), "Yet-Another-Passphrase-1"));
    }

    @Test
    void confirmResetWithExpiredTokenThrows() throws Exception {
        byte[] raw = newRawToken();
        insertToken(raw, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        assertThrows(InvalidOrExpiredResetTokenException.class,
                () -> service.confirmReset(encode(raw), NEW_PASSWORD));
    }

    @Test
    void confirmResetRevokesOtherActiveTokensOfSameAccount() throws Exception {
        byte[] rawUsed = newRawToken();
        byte[] rawOther = newRawToken();
        UUID usedId = insertActiveToken(rawUsed);
        UUID otherId = insertActiveToken(rawOther);

        service.confirmReset(encode(rawUsed), NEW_PASSWORD);

        assertNotNull(tokenColumn(usedId, "consumed_at"));
        assertNull(tokenColumn(usedId, "revoked_at"));
        assertNotNull(tokenColumn(otherId, "revoked_at"));
    }
}
