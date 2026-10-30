package com.jmcodestudio.agendamento.agenda;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Faixa enviada pelo fotógrafo antes da validação de negócio. endTime menor
 * ou igual a startTime é interpretado como uma faixa que atravessa a meia-
 * noite e é dividida em duas faixas persistidas (ver AgendaSettingsService).
 */
public record WeeklyScheduleRangeRequest(
        @NotNull @Min(0) @Max(6) Integer weekday,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime) {
}
