package com.jmcodestudio.agendamento.agenda;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jmcodestudio.agendamento.agenda.exception.BloqueioConflitanteException;
import com.jmcodestudio.agendamento.agenda.exception.BloqueioNaoEncontradoException;
import com.jmcodestudio.agendamento.agenda.exception.FaixaInvalidaException;

@Service
public class AgendaSettingsService {

    /**
     * SQLSTATE da exclusion constraint calendar_no_overlap (ver trigger
     * block_occupancy_sync em calendar_blocks). A agenda não valida
     * sobreposição com compromissos na aplicação — delega ao banco, que é
     * quem efetivamente conhece toda a ocupação (bloqueios + compromissos).
     */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    private final WeeklyScheduleRepository weeklyScheduleRepository;
    private final CalendarBlockRepository calendarBlockRepository;
    private final BookingHorizonRepository bookingHorizonRepository;

    public AgendaSettingsService(
            WeeklyScheduleRepository weeklyScheduleRepository,
            CalendarBlockRepository calendarBlockRepository,
            BookingHorizonRepository bookingHorizonRepository) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.calendarBlockRepository = calendarBlockRepository;
        this.bookingHorizonRepository = bookingHorizonRepository;
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public AgendaSettingsResponse getSettings(UUID photographerId) {
        // Must be the first statement in the transaction so RLS applies to everything after it.
        weeklyScheduleRepository.setTenantContext(photographerId);

        BookingHorizonRepository.TimezoneAndHorizon tz = bookingHorizonRepository.findTimezoneAndHorizon(photographerId);

        return new AgendaSettingsResponse(
                tz.timezone(),
                tz.bookingHorizonDate(),
                weeklyScheduleRepository.findAllByPhotographer(photographerId),
                calendarBlockRepository.findActiveByPhotographer(photographerId));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public List<WeeklyScheduleEntry> replaceWeeklySchedule(UUID photographerId, WeeklyScheduleRequest request) {
        List<WeeklyScheduleEntry> entries = splitAndValidate(request.ranges());

        // Must be the first statement in the transaction so RLS applies to everything after it.
        weeklyScheduleRepository.setTenantContext(photographerId);

        // DELETE + INSERT não é atômico contra outra chamada concorrente sob
        // READ COMMITTED (duas chamadas podem ambas apagar "nada novo" e depois
        // colidir na UNIQUE ao inserir). O lock serializa: a segunda chamada
        // espera a primeira confirmar antes de começar o próprio DELETE.
        weeklyScheduleRepository.lockPhotographer(photographerId);

        weeklyScheduleRepository.replaceAll(photographerId, entries);

        return weeklyScheduleRepository.findAllByPhotographer(photographerId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public CalendarBlock createBlock(UUID photographerId, CalendarBlockCreateRequest request) {
        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new FaixaInvalidaException("o início do bloqueio deve ser antes do término");
        }

        // Must be the first statement in the transaction so RLS applies to everything after it.
        calendarBlockRepository.setTenantContext(photographerId);

        try {
            return calendarBlockRepository.insert(
                    photographerId,
                    request.blockType(),
                    request.startsAt(),
                    request.endsAt(),
                    request.reason());
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) {
                throw new BloqueioConflitanteException();
            }
            throw e;
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<CalendarBlock> listBlocks(UUID photographerId) {
        // Must be the first statement in the transaction so RLS applies to everything after it.
        calendarBlockRepository.setTenantContext(photographerId);

        return calendarBlockRepository.findActiveByPhotographer(photographerId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteBlock(UUID photographerId, UUID blockId) {
        // Must be the first statement in the transaction so RLS applies to everything after it.
        calendarBlockRepository.setTenantContext(photographerId);

        int deleted = calendarBlockRepository.deleteById(photographerId, blockId);

        if (deleted == 0) {
            throw new BloqueioNaoEncontradoException();
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public LocalDate updateBookingHorizon(UUID photographerId, BookingHorizonRequest request) {
        // Must be the first statement in the transaction so RLS applies to everything after it.
        bookingHorizonRepository.setTenantContext(photographerId);

        bookingHorizonRepository.updateHorizon(photographerId, request.bookingHorizonDate());

        return request.bookingHorizonDate();
    }

    private static boolean isExclusionViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getRootCause();
        return cause instanceof PSQLException psqlException
                && SQLSTATE_EXCLUSION_VIOLATION.equals(psqlException.getSQLState());
    }

    /**
     * Converte as faixas enviadas pelo fotógrafo em faixas persistíveis:
     * - Faixas que atravessam a meia-noite (endTime <= startTime) são divididas
     *   em duas, uma terminando em 23:59:59 no dia original e outra começando
     *   em 00:00:00 no dia seguinte (perdendo, por aproximação, 1 segundo de
     *   disponibilidade à meia-noite — irrelevante na prática e documentado
     *   na decisão arquitetural da fase).
     * - Depois da divisão, valida que nenhuma faixa se sobrepõe a outra do
     *   mesmo dia (a UNIQUE do banco não impede sobreposição, só duplicidade
     *   exata).
     */
    private static List<WeeklyScheduleEntry> splitAndValidate(List<WeeklyScheduleRangeRequest> ranges) {
        List<WeeklyScheduleEntry> entries = new ArrayList<>();

        for (WeeklyScheduleRangeRequest range : ranges) {
            int weekday = range.weekday();
            LocalTime start = range.startTime();
            LocalTime end = range.endTime();

            if (start.equals(end)) {
                throw new FaixaInvalidaException("a faixa não pode ter início igual ao término");
            }

            if (end.equals(LocalTime.MIDNIGHT)) {
                // "até meia-noite" (ex.: 18:00 -> 00:00) significa até o fim do
                // dia, não uma faixa que avança para o dia seguinte. Tratar como
                // o caso geral de cruzamento de meia-noite geraria uma segunda
                // faixa (dia+1, 00:00, 00:00) com início igual ao término, o que
                // viola o CHECK (start_time < end_time) do banco.
                entries.add(new WeeklyScheduleEntry(weekday, start, LocalTime.of(23, 59, 59)));
            } else if (end.isAfter(start)) {
                entries.add(new WeeklyScheduleEntry(weekday, start, end));
            } else {
                // Atravessa a meia-noite de verdade (ex.: 22:00 -> 02:00): divide em duas faixas.
                entries.add(new WeeklyScheduleEntry(weekday, start, LocalTime.of(23, 59, 59)));
                entries.add(new WeeklyScheduleEntry((weekday + 1) % 7, LocalTime.MIDNIGHT, end));
            }
        }

        var byWeekday = entries.stream().collect(Collectors.groupingBy(WeeklyScheduleEntry::weekday));

        for (List<WeeklyScheduleEntry> dayEntries : byWeekday.values()) {
            List<WeeklyScheduleEntry> sorted = dayEntries.stream()
                    .sorted(Comparator.comparing(WeeklyScheduleEntry::startTime))
                    .toList();

            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).startTime().isBefore(sorted.get(i - 1).endTime())) {
                    throw new FaixaInvalidaException("faixas sobrepostas no mesmo dia da semana");
                }
            }
        }

        return entries;
    }

}
