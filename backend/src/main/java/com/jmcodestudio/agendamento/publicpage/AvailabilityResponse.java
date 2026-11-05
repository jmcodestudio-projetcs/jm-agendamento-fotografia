package com.jmcodestudio.agendamento.publicpage;

import java.time.OffsetDateTime;
import java.util.List;

public record AvailabilityResponse(List<OffsetDateTime> slots) {
}
