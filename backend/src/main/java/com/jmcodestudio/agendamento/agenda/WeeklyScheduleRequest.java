package com.jmcodestudio.agendamento.agenda;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Substitui a jornada semanal inteira do fotógrafo por este conjunto de
 * faixas. O limite de 50 é generoso (7 dias x múltiplas faixas) e existe só
 * para impedir um payload absurdo de gerar milhares de INSERTs.
 */
public record WeeklyScheduleRequest(@NotNull @Valid @Size(max = 50) List<WeeklyScheduleRangeRequest> ranges) {
}
