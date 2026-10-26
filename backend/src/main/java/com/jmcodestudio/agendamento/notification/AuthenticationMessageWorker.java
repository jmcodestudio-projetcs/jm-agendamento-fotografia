package com.jmcodestudio.agendamento.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.jmcodestudio.agendamento.passwordreset.AuthMessageEncryption;
import com.jmcodestudio.agendamento.signup.PlatformSettingsService;

/**
 * Envia as mensagens de autenticação pendentes. Claim e atualização de resultado
 * rodam em transações curtas e separadas; a chamada de rede ao Resend fica fora
 * de qualquer transação.
 */
@Component
public class AuthenticationMessageWorker {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationMessageWorker.class);

    private final AuthenticationMessageRepository repository;
    private final ResendEmailSender emailSender;
    private final AuthMessageEncryption encryption;
    private final PlatformSettingsService settings;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final String workerInstanceId = UUID.randomUUID().toString();

    public AuthenticationMessageWorker(
            AuthenticationMessageRepository repository,
            ResendEmailSender emailSender,
            AuthMessageEncryption encryption,
            PlatformSettingsService settings,
            @Qualifier("workerTransactionManager") PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.encryption = encryption;
        this.settings = settings;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // O intervalo vem de OUTBOX_POLL_SECONDS (padrão 5, igual ao seed de outbox_poll_seconds):
    // a anotação @Scheduled só aceita valor resolvido na inicialização, não lido do banco a cada ciclo.
    @Scheduled(fixedDelayString = "${OUTBOX_POLL_SECONDS:5}000")
    public void processPendingMessages() {
        int batchSize = settings.getIntSetting("outbox_batch_size");
        int leaseSeconds = settings.getIntSetting("work_lease_seconds");

        List<ClaimedMessage> batch = transactionTemplate.execute(
                status -> repository.claimBatch(workerInstanceId, batchSize, leaseSeconds));
        if (batch == null) {
            return;
        }

        for (ClaimedMessage message : batch) {
            try {
                process(message);
            } catch (Exception e) {
                log.error("Falha inesperada ao processar mensagem {}: {}", message.id(),
                        e.getClass().getSimpleName(), e);
            }
        }
    }

    private void process(ClaimedMessage message) {
        if (message.expiresAt().isBefore(Instant.now())) {
            inTransaction(() -> repository.markCanceledExpired(message.id()));
            return;
        }

        String subject;
        String text;
        try {
            byte[] plaintext = encryption.decrypt(message.payloadCiphertext(), message.encryptionKeyId());
            JsonNode json = jsonMapper.readTree(plaintext);
            subject = json.get("subject").asString();
            text = json.get("body").asString();
        } catch (Exception e) {
            // Nunca vai decifrar/interpretar: reprocessar não ajuda.
            String error = "Payload ilegível: " + e.getClass().getSimpleName();
            inTransaction(() -> repository.markFailedPermanently(message.id(), error));
            return;
        }

        SendResult result = emailSender.send(message.recipient(), subject, text);
        if (result.success()) {
            inTransaction(() -> repository.markSent(message.id(), result.providerId()));
            return;
        }

        int newAttemptCount = message.attemptCount() + 1;
        if (newAttemptCount >= settings.getIntSetting("notification_max_attempts")) {
            inTransaction(() -> repository.markFailedPermanently(message.id(), result.errorMessage()));
            return;
        }
        int delaySeconds = settings.getIntSetting(
                "retry_" + Math.min(newAttemptCount, 4) + "_seconds");
        Instant nextAvailableAt = Instant.now().plusSeconds(delaySeconds);
        inTransaction(() -> repository.markFailedForRetry(
                message.id(), newAttemptCount, nextAvailableAt, result.errorMessage()));
    }

    private void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
