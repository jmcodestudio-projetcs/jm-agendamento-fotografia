package com.jmcodestudio.agendamento.publicpage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jmcodestudio.agendamento.agenda.WeeklyScheduleEntry;
import com.jmcodestudio.agendamento.catalog.ServiceOffering;
import com.jmcodestudio.agendamento.publicpage.exception.FotografoNaoEncontradoException;
import com.jmcodestudio.agendamento.publicpage.exception.ServicoNaoDisponivelException;

@Service
public class PublicCatalogService {

    private final PublicPhotographerRepository photographerRepository;
    private final PublicServiceRepository serviceRepository;
    private final PublicWeeklyScheduleRepository scheduleRepository;
    private final PublicOccupancyRepository occupancyRepository;

    public PublicCatalogService(
            PublicPhotographerRepository photographerRepository,
            PublicServiceRepository serviceRepository,
            PublicWeeklyScheduleRepository scheduleRepository,
            PublicOccupancyRepository occupancyRepository) {
        this.photographerRepository = photographerRepository;
        this.serviceRepository = serviceRepository;
        this.scheduleRepository = scheduleRepository;
        this.occupancyRepository = occupancyRepository;
    }

    /**
     * Único método que não trata "indisponível" como 404 — a página precisa
     * saber que o fotógrafo existe, para mostrar "agenda temporariamente
     * indisponível" (CA-030) em vez de "página não encontrada".
     */
    @Transactional(transactionManager = "publicTransactionManager", readOnly = true)
    public PublicPhotographerResponse findPhotographer(String slug) {
        PublicPhotographer photographer = photographerRepository.findBySlug(slug)
                .orElseThrow(FotografoNaoEncontradoException::new);
        return new PublicPhotographerResponse(
                photographer.name(), photographer.timezone(), photographer.isPageAvailable());
    }

    @Transactional(transactionManager = "publicTransactionManager", readOnly = true)
    public List<PublicServiceResponse> listServices(String slug) {
        PublicPhotographer photographer = loadAvailablePhotographer(slug);
        // Must be the first statement after loading the photographer, before any
        // other query scoped by photographer_id (same convention as jm_tenant).
        photographerRepository.setPublicContext(photographer.id());

        return serviceRepository.findActiveByPhotographer(photographer.id()).stream()
                .map(PublicCatalogService::toServiceResponse)
                .toList();
    }

    @Transactional(transactionManager = "publicTransactionManager", readOnly = true)
    public AvailabilityResponse availability(String slug, UUID serviceId, LocalDate date) {
        PublicPhotographer photographer = loadAvailablePhotographer(slug);
        photographerRepository.setPublicContext(photographer.id());

        ServiceOffering service = serviceRepository.findById(photographer.id(), serviceId)
                .filter(ServiceOffering::active)
                .orElseThrow(ServicoNaoDisponivelException::new);

        ZoneId zone = ZoneId.of(photographer.timezone());
        List<WeeklyScheduleEntry> schedule = scheduleRepository.findAllByPhotographer(photographer.id());

        // Janela generosa (o dia todo no fuso do fotógrafo); o AvailabilityCalculator
        // faz o teste de sobreposição exato por slot.
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<OccupiedInterval> occupancy = occupancyRepository.findOverlapping(photographer.id(), dayStart, dayEnd);

        List<Instant> slots = AvailabilityCalculator.computeSlotStarts(
                date,
                zone,
                schedule,
                occupancy,
                service.durationMinutes(),
                service.minimumNoticeMinutes(),
                service.bufferAfterMinutes(),
                photographer.bookingHorizonDate(),
                Instant.now());

        return new AvailabilityResponse(slots.stream().map(s -> s.atZone(zone).toOffsetDateTime()).toList());
    }

    /** CA-030: conta indisponível recusa catálogo/disponibilidade com o mesmo 404 genérico de slug inexistente. */
    private PublicPhotographer loadAvailablePhotographer(String slug) {
        PublicPhotographer photographer = photographerRepository.findBySlug(slug)
                .orElseThrow(FotografoNaoEncontradoException::new);
        if (!photographer.isPageAvailable()) {
            throw new FotografoNaoEncontradoException();
        }
        return photographer;
    }

    private static PublicServiceResponse toServiceResponse(ServiceOffering s) {
        return new PublicServiceResponse(
                s.id(), s.name(), s.description(), s.durationMinutes(),
                s.pricePublic() ? s.priceAmount() : null, s.pricePublic());
    }
}
