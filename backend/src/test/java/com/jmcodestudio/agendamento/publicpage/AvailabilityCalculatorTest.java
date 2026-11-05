package com.jmcodestudio.agendamento.publicpage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jmcodestudio.agendamento.agenda.WeeklyScheduleEntry;

/**
 * Testes puros do motor de disponibilidade (Fase 4.1) — sem Spring, sem
 * Testcontainers: AvailabilityCalculator não depende de banco.
 */
class AvailabilityCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Test
    void generatesOneSlotPerDurationAcrossTheWholeWindow() {
        // Quarta-feira (weekday 3), 09:00-12:00, serviço de 60 minutos -> 3 slots.
        LocalDate wednesday = nextDateWithWeekday(3);
        List<WeeklyScheduleEntry> schedule = List.of(
                new WeeklyScheduleEntry(3, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        List<Instant> slots = AvailabilityCalculator.computeSlotStarts(
                wednesday, ZONE, schedule, List.of(), 60, 0, 0, null, Instant.EPOCH);

        assertEquals(3, slots.size());
        assertEquals(zonedInstant(wednesday, 9, 0), slots.get(0));
        assertEquals(zonedInstant(wednesday, 10, 0), slots.get(1));
        assertEquals(zonedInstant(wednesday, 11, 0), slots.get(2));
    }

    @Test
    void occupancyRemovesOverlappingSlotsButKeepsTheRest() {
        LocalDate wednesday = nextDateWithWeekday(3);
        List<WeeklyScheduleEntry> schedule = List.of(
                new WeeklyScheduleEntry(3, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        List<OccupiedInterval> occupancy = List.of(
                new OccupiedInterval(zonedInstant(wednesday, 10, 0), zonedInstant(wednesday, 11, 0)));

        List<Instant> slots = AvailabilityCalculator.computeSlotStarts(
                wednesday, ZONE, schedule, occupancy, 60, 0, 0, null, Instant.EPOCH);

        assertEquals(List.of(zonedInstant(wednesday, 9, 0), zonedInstant(wednesday, 11, 0)), slots);
    }

    @Test
    void bufferAfterServiceBlocksTheAdjacentSlot() {
        // 09:00-11:00, serviço de 60 min + buffer de 30 min -> só cabe um slot (09:00),
        // porque 10:00 + 60min + 30min buffer (10:90) passaria de 11:00.
        LocalDate wednesday = nextDateWithWeekday(3);
        List<WeeklyScheduleEntry> schedule = List.of(
                new WeeklyScheduleEntry(3, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        List<Instant> slots = AvailabilityCalculator.computeSlotStarts(
                wednesday, ZONE, schedule, List.of(), 60, 0, 30, null, Instant.EPOCH);

        assertEquals(List.of(zonedInstant(wednesday, 9, 0)), slots);
    }

    @Test
    void minimumNoticeFiltersOutSlotsTooCloseToNow() {
        LocalDate wednesday = nextDateWithWeekday(3);
        List<WeeklyScheduleEntry> schedule = List.of(
                new WeeklyScheduleEntry(3, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        // "Agora" é 9:30 do mesmo dia; antecedência mínima de 60 min -> limite é 10:30,
        // então 9:00 e 10:00 ficam de fora (começam antes do limite) e só 11:00 sobra.
        Instant now = zonedInstant(wednesday, 9, 30);

        List<Instant> slots = AvailabilityCalculator.computeSlotStarts(
                wednesday, ZONE, schedule, List.of(), 60, 60, 0, null, now);

        assertEquals(List.of(zonedInstant(wednesday, 11, 0)), slots);
    }

    @Test
    void bookingHorizonRejectsDatesBeyondIt() {
        LocalDate wednesday = nextDateWithWeekday(3);
        List<WeeklyScheduleEntry> schedule = List.of(
                new WeeklyScheduleEntry(3, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        List<Instant> slots = AvailabilityCalculator.computeSlotStarts(
                wednesday, ZONE, schedule, List.of(), 60, 0, 0, wednesday.minusDays(1), Instant.EPOCH);

        assertTrue(slots.isEmpty());
    }

    @Test
    void ignoresRangesOfOtherWeekdays() {
        LocalDate wednesday = nextDateWithWeekday(3);
        List<WeeklyScheduleEntry> schedule = List.of(
                new WeeklyScheduleEntry(2, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        List<Instant> slots = AvailabilityCalculator.computeSlotStarts(
                wednesday, ZONE, schedule, List.of(), 60, 0, 0, null, Instant.EPOCH);

        assertTrue(slots.isEmpty());
    }

    private static Instant zonedInstant(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ZONE).toInstant();
    }

    /** Convenção 0=domingo..6=sábado, igual ao Postgres (ver AvailabilityCalculator). */
    private static LocalDate nextDateWithWeekday(int dbWeekday) {
        LocalDate date = LocalDate.now().plusYears(1);
        while (toDbWeekday(date) != dbWeekday) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static int toDbWeekday(LocalDate date) {
        return date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? 0 : date.getDayOfWeek().getValue();
    }
}
