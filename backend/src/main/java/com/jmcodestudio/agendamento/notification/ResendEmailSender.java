package com.jmcodestudio.agendamento.notification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envia e-mail via API do Resend. Nunca loga o corpo da requisição (contém o link
 * de recuperação em texto puro): só destinatário e status da resposta.
 */
@Component
public class ResendEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final URI ENDPOINT = URI.create("https://api.resend.com/emails");

    private final String apiKey;
    private final String fromEmail;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public ResendEmailSender(
            @Value("${RESEND_API_KEY}") String apiKey,
            @Value("${RESEND_FROM_EMAIL}") String fromEmail) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
    }

    public SendResult send(String recipientEmail, String subject, String textBody) {
        try {
            String json = jsonMapper.writeValueAsString(Map.of(
                    "from", fromEmail,
                    "to", List.of(recipientEmail),
                    "subject", subject,
                    "text", textBody));

            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            log.info("Resend: destinatário={} status={}", recipientEmail, status);

            if (status >= 200 && status < 300) {
                JsonNode id = jsonMapper.readTree(response.body()).get("id");
                return new SendResult(true, id == null ? null : id.asString(), null);
            }
            return new SendResult(false, null, "HTTP " + status + ": " + response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SendResult(false, null, String.valueOf(e.getMessage()));
        } catch (Exception e) {
            log.warn("Resend: falha ao enviar para {}: {}", recipientEmail, e.getClass().getSimpleName());
            return new SendResult(false, null, String.valueOf(e.getMessage()));
        }
    }
}
