package com.jmcodestudio.agendamento.agenda;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CalendarBlock(
        UUID id,
        BlockType blockType,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String reason) {
}
