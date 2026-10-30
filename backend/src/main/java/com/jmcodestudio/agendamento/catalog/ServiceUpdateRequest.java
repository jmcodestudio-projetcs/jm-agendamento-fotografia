package com.jmcodestudio.agendamento.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ServiceUpdateRequest(
        @NotBlank @Size(min = 1, max = 150) String name,
        String description,
        @NotNull @Min(1) Integer durationMinutes,
        @DecimalMin("0.0") BigDecimal priceAmount,
        @NotNull Boolean pricePublic,
        @NotNull @Min(0) Integer minimumNoticeMinutes,
        @NotNull @Min(0) Integer bufferAfterMinutes) {
}
