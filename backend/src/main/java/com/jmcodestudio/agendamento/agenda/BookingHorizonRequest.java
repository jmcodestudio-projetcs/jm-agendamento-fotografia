package com.jmcodestudio.agendamento.agenda;

import java.time.LocalDate;

/** bookingHorizonDate nulo significa "sem limite definido" (coluna é opcional). */
public record BookingHorizonRequest(LocalDate bookingHorizonDate) {
}
