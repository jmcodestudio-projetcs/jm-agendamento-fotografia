package com.jmcodestudio.agendamento.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOffering(
        UUID id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal priceAmount,
        boolean pricePublic,
        int minimumNoticeMinutes,
        int bufferAfterMinutes,
        boolean active) {
}
