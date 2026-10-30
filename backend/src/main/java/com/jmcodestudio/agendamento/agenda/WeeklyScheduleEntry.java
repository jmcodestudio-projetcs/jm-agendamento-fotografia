package com.jmcodestudio.agendamento.agenda;

import java.time.LocalTime;

/**
 * Uma faixa de atendimento já persistida/validada para um dia da semana.
 * weekday segue a convenção 0 = domingo ... 6 = sábado (igual a
 * EXTRACT(DOW ...) do Postgres e a Date.getDay() do JavaScript).
 */
public record WeeklyScheduleEntry(int weekday, LocalTime startTime, LocalTime endTime) {
}
