package com.jmcodestudio.agendamento.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jmcodestudio.agendamento.config.WorkerDataSourceConfig;

/**
 * Cobre o achado A-3 da auditoria independente de 30/10/2026: antes desta
 * correção, claimBatch só selecionava status='PENDING', então uma mensagem
 * que ficasse travada em PROCESSING (worker caiu, app reiniciou entre o
 * claim e o markSent/markFailed*) nunca mais era reivindicada -- a
 * fotógrafa nunca recebia o e-mail de recuperação, sem erro visível.
 */
@SpringJUnitConfig
class AuthenticationMessageRepositoryIT {

    private static final String WORKER_USER = "agendamento_worker";
    private static final String WORKER_PASSWORD = "worker_test_pw";

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
        admin.execute("CREATE ROLE " + WORKER_USER + " LOGIN PASSWORD '" + WORKER_PASSWORD + "'");
        admin.execute("GRANT jm_worker TO " + WORKER_USER);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(5432));
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("DB_WORKER_USER", () -> WORKER_USER);
        registry.add("DB_WORKER_PASSWORD", () -> WORKER_PASSWORD);
    }

    @Configuration
    @EnableTransactionManagement
    @Import({ WorkerDataSourceConfig.class, AuthenticationMessageRepository.class })
    static class TestConfig {

        @Bean
        TransactionTemplate transactionTemplate(
                @org.springframework.beans.factory.annotation.Qualifier("workerTransactionManager")
                PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private AuthenticationMessageRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // authentication_messages exige exatamente uma FK não-nula entre
    // password_reset_token_id e admin_login_challenge_id
    // (CHECK(num_nonnulls(...)=1), nomeado "authentication_messages_check" por
    // ser o primeiro CHECK de tabela sem nome explícito -- não tem nada a ver
    // com a validação de payload, e foi o que quebrou na primeira versão deste
    // teste). Por isso o setup cria um password_reset_tokens de suporte (preso
    // a um admin_users fictício, caminho com menos FKs em cadeia do que
    // photographer_user_id) só para satisfazer essa constraint.
    private UUID resetTokenId;

    @BeforeEach
    void resetData() {
        admin.execute("TRUNCATE scheduling.authentication_messages, "
                + "scheduling.password_reset_tokens, scheduling.admin_users CASCADE");

        UUID adminUserId = UUID.randomUUID();
        admin.update("INSERT INTO scheduling.admin_users (id, email_normalized) VALUES (?, ?)",
                adminUserId, "admin-" + adminUserId + "@example.com");

        resetTokenId = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.password_reset_tokens (id, admin_user_id, token_hash, expires_at)
                VALUES (?, ?, ?, now() + interval '1 hour')
                """, resetTokenId, adminUserId, new byte[32]);
    }

    private UUID insertMessage(String status, Instant lockedUntil) {
        UUID id = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.authentication_messages
                    (id, password_reset_token_id, recipient, payload_ciphertext, encryption_key_id,
                     status, available_at, expires_at, locked_until, lock_owner)
                VALUES (?, ?, 'maria@example.com', 'x'::bytea, 'key-1', ?,
                         now() - interval '1 minute', now() + interval '1 hour', ?, ?)
                """, id, resetTokenId, status, lockedUntil == null ? null : Timestamp.from(lockedUntil),
                lockedUntil == null ? null : UUID.randomUUID());
        return id;
    }

    private List<ClaimedMessage> claim() {
        return transactionTemplate.execute(status ->
                repository.claimBatch(UUID.randomUUID().toString(), 10, 300));
    }

    @Test
    void claimsPendingMessages() {
        UUID id = insertMessage("PENDING", null);

        List<ClaimedMessage> claimed = claim();

        assertEquals(1, claimed.size());
        assertEquals(id, claimed.get(0).id());
    }

    @Test
    void doesNotClaimProcessingMessageWithLeaseStillValid() {
        insertMessage("PROCESSING", Instant.now().plusSeconds(600));

        List<ClaimedMessage> claimed = claim();

        assertTrue(claimed.isEmpty());
    }

    @Test
    void reclaimsProcessingMessageWithExpiredLease() {
        UUID id = insertMessage("PROCESSING", Instant.now().minusSeconds(60));

        List<ClaimedMessage> claimed = claim();

        assertEquals(1, claimed.size());
        assertEquals(id, claimed.get(0).id());

        Map<String, Object> row = admin.queryForMap(
                "SELECT status, locked_until FROM scheduling.authentication_messages WHERE id = ?", id);
        assertEquals("PROCESSING", row.get("status"));
        assertTrue(((Timestamp) row.get("locked_until")).toInstant().isAfter(Instant.now()));
    }
}
