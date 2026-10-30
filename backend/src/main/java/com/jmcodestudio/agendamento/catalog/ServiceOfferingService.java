package com.jmcodestudio.agendamento.catalog;

import java.util.List;
import java.util.UUID;

import com.jmcodestudio.agendamento.catalog.exception.PrecoPublicoSemValorException;
import com.jmcodestudio.agendamento.catalog.exception.ServicoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceOfferingService {

    private final ServiceOfferingRepository repository;

    public ServiceOfferingService(ServiceOfferingRepository repository) {
        this.repository = repository;
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<ServiceResponse> list(UUID photographerId) {
        // Must be the first statement in the transaction so RLS applies to everything after it.
        repository.setTenantContext(photographerId);

        return repository.findAllByPhotographer(photographerId).stream()
                .map(ServiceOfferingService::toResponse)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public ServiceResponse create(UUID photographerId, ServiceCreateRequest request) {
        validatePublicPrice(request.pricePublic(), request.priceAmount());

        // Must be the first statement in the transaction so RLS applies to everything after it.
        repository.setTenantContext(photographerId);

        ServiceOffering created = repository.insert(
                photographerId,
                request.name().trim(),
                request.description(),
                request.durationMinutes(),
                request.priceAmount(),
                request.pricePublic(),
                request.minimumNoticeMinutes(),
                request.bufferAfterMinutes());

        return toResponse(created);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public ServiceResponse update(UUID photographerId, UUID serviceId, ServiceUpdateRequest request) {
        validatePublicPrice(request.pricePublic(), request.priceAmount());

        // Must be the first statement in the transaction so RLS applies to everything after it.
        repository.setTenantContext(photographerId);

        int updated = repository.update(
                photographerId,
                serviceId,
                request.name().trim(),
                request.description(),
                request.durationMinutes(),
                request.priceAmount(),
                request.pricePublic(),
                request.minimumNoticeMinutes(),
                request.bufferAfterMinutes());

        if (updated == 0) {
            throw new ServicoNaoEncontradoException();
        }

        // The request has no "active" field, so reload to return its current value.
        return reload(photographerId, serviceId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public ServiceResponse updateActive(UUID photographerId, UUID serviceId, ActiveStatusRequest request) {
        // Must be the first statement in the transaction so RLS applies to everything after it.
        repository.setTenantContext(photographerId);

        int updated = repository.updateActive(photographerId, serviceId, request.active());

        if (updated == 0) {
            throw new ServicoNaoEncontradoException();
        }

        return reload(photographerId, serviceId);
    }

    private ServiceResponse reload(UUID photographerId, UUID serviceId) {
        return repository.findAllByPhotographer(photographerId).stream()
                .filter(s -> s.id().equals(serviceId))
                .findFirst()
                .map(ServiceOfferingService::toResponse)
                .orElseThrow(ServicoNaoEncontradoException::new);
    }

    private static void validatePublicPrice(Boolean pricePublic, java.math.BigDecimal priceAmount) {
        if (Boolean.TRUE.equals(pricePublic) && priceAmount == null) {
            throw new PrecoPublicoSemValorException();
        }
    }

    private static ServiceResponse toResponse(ServiceOffering s) {
        return new ServiceResponse(
                s.id(),
                s.name(),
                s.description(),
                s.durationMinutes(),
                s.priceAmount(),
                s.pricePublic(),
                s.minimumNoticeMinutes(),
                s.bufferAfterMinutes(),
                s.active());
    }
}
