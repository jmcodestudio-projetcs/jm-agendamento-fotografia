package com.jmcodestudio.agendamento.trial;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jmcodestudio.agendamento.config.WorkerDataSourceConfig;
import com.jmcodestudio.agendamento.notification.ResendEmailSender;
import com.jmcodestudio.agendamento.notification.SendResult;

@SpringJUnitConfig
class TrialLifecycleServiceIT {

    private static final String WORKER_USER = "agendamento_worker_test";
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
        // Passo manual em produção: o login do worker herda jm_worker (NOBYPASSRLS).
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

    /** Substitui o envio real (Resend) e registra as chamadas. */
    static class FakeResendEmailSender extends ResendEmailSender {

        record Sent(String recipient, String subject, String body) {
        }

        final List<Sent> sent = new ArrayList<>();
        boolean succeed = true;

        FakeResendEmailSender() {
            super("fake-key", "fake@example.com");
        }

        @Override
        public SendResult send(String recipientEmail, String subject, String textBody) {
            sent.add(new Sent(recipientEmail, subject, textBody));
            return succeed
                    ? new SendResult(true, "fake-id", null)
                    : new SendResult(false, null, "falha simulada");
        }
    }

    @Configuration
    @EnableTransactionManagement
    @Import({ WorkerDataSourceConfig.class, TrialLifecycleRepository.class, TrialLifecycleService.class })
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        @Primary
        FakeResendEmailSender fakeResendEmailSender() {
            return new FakeResendEmailSender();
        }
    }

    @Autowired
    private TrialLifecycleService service;

    @Autowired
    private FakeResendEmailSender emailSender;

    @BeforeEach
    void resetData() {
        admin.execute("TRUNCATE scheduling.photographers CASCADE");
        emailSender.sent.clear();
        emailSender.succeed = true;
    }

    private record Account(UUID photographerId, UUID subscriptionId) {
    }

    private Account insertAccount(String email, Instant trialEndsAt, Instant warning7dSentAt) {
        UUID photographerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        admin.update("""
                INSERT INTO scheduling.photographers
                    (id, name, email, email_normalized, public_slug, timezone, account_status,
                     public_page_enabled, push_enabled)
                VALUES (?, ?, ?, ?, ?, 'America/Sao_Paulo', 'EM_TESTE', true, false)
                """, photographerId, "Fotógrafo " + email, email, email, "slug-" + photographerId);
        admin.update("""
                INSERT INTO scheduling.subscriptions
                    (id, photographer_id, status, trial_started_at, trial_ends_at, trial_warning_7d_sent_at)
                VALUES (?, ?, 'TRIAL', ?, ?, ?)
                """, subscriptionId, photographerId,
                Timestamp.from(trialEndsAt.minus(30, ChronoUnit.DAYS)),
                Timestamp.from(trialEndsAt),
                warning7dSentAt == null ? null : Timestamp.from(warning7dSentAt));
        return new Account(photographerId, subscriptionId);
    }

    private Map<String, Object> photographer(Account account) {
        return admin.queryForMap("""
                SELECT account_status, suspended_at, suspension_reason
                FROM scheduling.photographers WHERE id = ?
                """, account.photographerId());
    }

    private Map<String, Object> subscription(Account account) {
        return admin.queryForMap("""
                SELECT status, trial_warning_7d_sent_at, trial_warning_1d_sent_at
                FROM scheduling.subscriptions WHERE id = ?
                """, account.subscriptionId());
    }

    @Test
    void testeAindaLongeDoFimNaoEnviaNadaNemAlteraConta() {
        Account account = insertAccount("longe@example.com", Instant.now().plus(20, ChronoUnit.DAYS), null);

        service.checkTrialLifecycle();

        assertTrue(emailSender.sent.isEmpty());
        Map<String, Object> sub = subscription(account);
        assertNull(sub.get("trial_warning_7d_sent_at"));
        assertNull(sub.get("trial_warning_1d_sent_at"));
        assertEquals("TRIAL", sub.get("status"));
        assertEquals("EM_TESTE", photographer(account).get("account_status"));
    }

    @Test
    void testeNaJanelaDeSeteDiasEnviaAvisoEMarcaColuna() {
        Account account = insertAccount("sete@example.com", Instant.now().plus(6, ChronoUnit.DAYS), null);

        service.checkTrialLifecycle();

        assertEquals(1, emailSender.sent.size());
        assertEquals("sete@example.com", emailSender.sent.get(0).recipient());
        assertEquals("Seu teste termina em 7 dias", emailSender.sent.get(0).subject());
        assertNotNull(subscription(account).get("trial_warning_7d_sent_at"));
        assertNull(subscription(account).get("trial_warning_1d_sent_at"));
    }

    @Test
    void avisoDeSeteDiasJaEnviadoNaoEhReenviado() {
        Account account = insertAccount("jaenviado@example.com", Instant.now().plus(6, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));

        service.checkTrialLifecycle();

        assertTrue(emailSender.sent.isEmpty());
        assertNotNull(subscription(account).get("trial_warning_7d_sent_at"));
    }

    @Test
    void testeNaJanelaDeUmDiaEnviaAvisoEMarcaColuna() {
        // 7d já enviado para isolar o aviso de 1 dia (12h também cai na janela de 7 dias)
        Account account = insertAccount("umdia@example.com", Instant.now().plus(12, ChronoUnit.HOURS),
                Instant.now().minus(6, ChronoUnit.DAYS));

        service.checkTrialLifecycle();

        assertEquals(1, emailSender.sent.size());
        assertEquals("umdia@example.com", emailSender.sent.get(0).recipient());
        assertEquals("Seu teste termina amanhã", emailSender.sent.get(0).subject());
        assertNotNull(subscription(account).get("trial_warning_1d_sent_at"));
    }

    @Test
    void testeExpiradoSuspendeContaEncerraAssinaturaEEnviaEmail() {
        Account account = insertAccount("expirado@example.com", Instant.now().minus(1, ChronoUnit.HOURS), null);

        service.checkTrialLifecycle();

        Map<String, Object> photographer = photographer(account);
        assertEquals("SUSPENSA", photographer.get("account_status"));
        assertNotNull(photographer.get("suspended_at"));
        assertEquals("TRIAL_ENDED", photographer.get("suspension_reason"));
        assertEquals("ENDED", subscription(account).get("status"));
        assertEquals(1, emailSender.sent.size());
        assertEquals("Seu período de teste terminou", emailSender.sent.get(0).subject());
        assertEquals("expirado@example.com", emailSender.sent.get(0).recipient());
    }

    @Test
    void suspensaoEhIdempotenteEmExecucoesRepetidas() {
        Account account = insertAccount("idempotente@example.com", Instant.now().minus(1, ChronoUnit.HOURS), null);

        service.checkTrialLifecycle();
        Object suspendedAt = photographer(account).get("suspended_at");

        assertDoesNotThrow(() -> service.checkTrialLifecycle());

        assertEquals(suspendedAt, photographer(account).get("suspended_at"));
        assertEquals("SUSPENSA", photographer(account).get("account_status"));
        assertEquals("ENDED", subscription(account).get("status"));
        assertEquals(1, emailSender.sent.size());
    }

    @Test
    void falhaNoEnvioDoAvisoDeSeteDiasNaoMarcaColunaENaoLancaExcecao() {
        Account account = insertAccount("falha@example.com", Instant.now().plus(6, ChronoUnit.DAYS), null);
        emailSender.succeed = false;

        assertDoesNotThrow(() -> service.checkTrialLifecycle());

        assertEquals(1, emailSender.sent.size());
        assertNull(subscription(account).get("trial_warning_7d_sent_at"));
    }
}
