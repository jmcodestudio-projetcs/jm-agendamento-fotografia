package com.jmcodestudio.agendamento.agenda;

import java.time.LocalDate;
import java.util.List;

public record AgendaSettingsResponse(
        String timezone,
        LocalDate bookingHorizonDate,
        List<WeeklyScheduleEntry> weeklySchedule,
        List<CalendarBlock> calendarBlocks) {
}
