package com.jmcodestudio.agendamento.publicpage;

import java.time.Instant;

/** Um intervalo ocupado, lido de calendar_occupancy. */
public record OccupiedInterval(Instant startsAt, Instant endsAt) {
}
