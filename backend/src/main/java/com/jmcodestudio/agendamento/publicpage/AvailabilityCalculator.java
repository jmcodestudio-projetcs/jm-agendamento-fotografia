package com.jmcodestudio.agendamento.publicpage;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.jmcodestudio.agendamento.agenda.WeeklyScheduleEntry;

/**
 * Motor de cálculo de disponibilidade (Fase 4.1, absorve a antiga 3.2).
 * Classe pura — sem Spring, sem banco — pra ser testada com JUnit simples,
 * sem Testcontainers (ver AvailabilityCalculatorTest).
 *
 * Decisões de desenho não especificadas pela baseline, documentadas em
 * claude/fase-4.1-pagina-publica-disponibilidade.md:
 * - Granularidade dos horários oferecidos: um slot a cada duration_minutes
 *   do próprio serviço, a partir do início de cada faixa da jornada — não
 *   um grid fixo de 15/30 minutos.
 * - Horário local ambíguo ou inexistente (mudança de horário de verão):
 *   resolvido pelo comportamento padrão de ZonedDateTime.of (avança em
 *   lacunas, usa o deslocamento anterior em sobreposições) — pendência
 *   deixada em aberto desde a 3.1, adotada aqui porque agora existe
 *   consumidor real.
 * - Faixa que um serviço atravessaria a meia-noite: como a 3.1 já persiste
 *   faixas sem end_time=00:00 (vira 23:59:59 do mesmo dia), um serviço cujo
 *   término ultrapassaria o fim da faixa simplesmente não é oferecido
 *   nesse dia — limitação conhecida, já documentada na 3.1.
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    public static List<Instant> computeSlotStarts(
            LocalDate date,
            ZoneId zone,
            List<WeeklyScheduleEntry> weeklySchedule,
            List<OccupiedInterval> occupancy,
            int durationMinutes,
            int minimumNoticeMinutes,
            int bufferAfterMinutes,
            LocalDate bookingHorizonDate,
            Instant now) {

        if (bookingHorizonDate != null && date.isAfter(bookingHorizonDate)) {
            return List.of();
        }

        int weekday = toDbWeekday(date.getDayOfWeek());
        Duration serviceDuration = Duration.ofMinutes(durationMinutes);
        Duration occupiedDuration = Duration.ofMinutes((long) durationMinutes + bufferAfterMinutes);
        Instant earliestAllowed = now.plus(Duration.ofMinutes(minimumNoticeMinutes));

        List<Instant> slots = new ArrayList<>();
        for (WeeklyScheduleEntry range : weeklySchedule) {
            if (range.weekday() != weekday) {
                continue;
            }
            ZonedDateTime windowStart = ZonedDateTime.of(date, range.startTime(), zone);
            ZonedDateTime windowEnd = ZonedDateTime.of(date, range.endTime(), zone);

            ZonedDateTime candidate = windowStart;
            while (!candidate.plus(occupiedDuration).isAfter(windowEnd)) {
                Instant candidateStart = candidate.toInstant();
                Instant candidateOccupiedEnd = candidate.plus(occupiedDuration).toInstant();

                boolean respectsNotice = !candidateStart.isBefore(earliestAllowed);
                boolean free = occupancy.stream().noneMatch(occ ->
                        candidateStart.isBefore(occ.endsAt()) && occ.startsAt().isBefore(candidateOccupiedEnd));

                if (respectsNotice && free) {
                    slots.add(candidateStart);
                }

                candidate = candidate.plus(serviceDuration);
            }
        }
        slots.sort(Instant::compareTo);
        return slots;
    }

    /** 0 = domingo ... 6 = sábado, igual ao EXTRACT(DOW ...) do Postgres (mesma convenção da 3.1). */
    private static int toDbWeekday(DayOfWeek javaDay) {
        return javaDay == DayOfWeek.SUNDAY ? 0 : javaDay.getValue();
    }
}
