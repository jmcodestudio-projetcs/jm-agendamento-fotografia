package com.jmcodestudio.agendamento.agenda;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

public record CalendarBlockCreateRequest(
        @NotNull BlockType blockType,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        String reason) {
}
