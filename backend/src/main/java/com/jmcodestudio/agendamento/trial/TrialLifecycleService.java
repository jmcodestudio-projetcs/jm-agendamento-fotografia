package com.jmcodestudio.agendamento.trial;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jmcodestudio.agendamento.notification.ResendEmailSender;
import com.jmcodestudio.agendamento.notification.SendResult;

@Service
public class TrialLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TrialLifecycleService.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);
    private static final String ACTIVATION_HINT =
            ". Para continuar usando a plataforma sem interrupção, entre em contato com a JM Code Studio para ativar sua assinatura.";

    private final TrialLifecycleRepository repository;
    private final ResendEmailSender resendEmailSender;

    public TrialLifecycleService(TrialLifecycleRepository repository, ResendEmailSender resendEmailSender) {
        this.repository = repository;
        this.resendEmailSender = resendEmailSender;
    }

    public void checkTrialLifecycle() {
        sendWarnings(
                repository.findDueForSevenDayWarning(),
                "Seu teste termina em 7 dias",
                "termina em 7 dias",
                repository::markSevenDayWarningSent);
        sendWarnings(
                repository.findDueForOneDayWarning(),
                "Seu teste termina amanhã",
                "termina amanhã",
                repository::markOneDayWarningSent);
        suspendExpired();
    }

    private void sendWarnings(List<TrialCandidate> candidates, String subject, String endsPhrase,
            Consumer<java.util.UUID> markSent) {
        for (TrialCandidate candidate : candidates) {
            try {
                String body = "Olá, " + candidate.name() + ". Seu período de teste na JM Code Studio "
                        + endsPhrase + ", em " + DATE_FORMAT.format(candidate.trialEndsAt())
                        + ACTIVATION_HINT;
                SendResult result = resendEmailSender.send(candidate.email(), subject, body);
                if (result.success()) {
                    markSent.accept(candidate.subscriptionId());
                } else {
                    log.warn("Falha ao enviar aviso de teste para {}: {}", candidate.email(), result.errorMessage());
                }
            } catch (Exception e) {
                log.warn("Erro ao processar aviso de teste para {}: {}", candidate.email(), e.getMessage());
            }
        }
    }

    private void suspendExpired() {
        for (TrialCandidate candidate : repository.findExpired()) {
            try {
                repository.suspendForTrialEnded(candidate.photographerId(), candidate.subscriptionId());
                String body = "Olá, " + candidate.name() + ". Seu período de teste de 30 dias terminou e sua conta"
                        + " foi suspensa. Seus dados e compromissos estão preservados."
                        + " Para reativar, entre em contato com a JM Code Studio.";
                SendResult result = resendEmailSender.send(candidate.email(), "Seu período de teste terminou", body);
                if (!result.success()) {
                    log.warn("Falha ao enviar aviso de término para {}: {}", candidate.email(), result.errorMessage());
                }
            } catch (Exception e) {
                log.warn("Erro ao processar término de teste para {}: {}", candidate.email(), e.getMessage());
            }
        }
    }
}
