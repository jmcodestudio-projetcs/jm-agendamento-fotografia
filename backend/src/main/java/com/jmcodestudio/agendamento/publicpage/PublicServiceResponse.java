package com.jmcodestudio.agendamento.publicpage;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * priceAmount só vem preenchido quando pricePublic=true (RN-004B); do
 * contrário, a tela deve mostrar "Valor sob consulta". minimumNoticeMinutes
 * e bufferAfterMinutes não são expostos aqui — são detalhe operacional do
 * motor de disponibilidade, não informação que o cliente final precisa ver.
 */
public record PublicServiceResponse(
        UUID id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal priceAmount,
        boolean pricePublic) {
}
