package com.jmcodestudio.agendamento.profile;

import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.UUID;

import com.jmcodestudio.agendamento.signup.exception.TimezoneInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotographerProfileService {

    private final PhotographerProfileRepository repository;

    public PhotographerProfileService(PhotographerProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public ProfileResponse getProfile(UUID photographerId) {
        repository.setTenantContext(photographerId);

        PhotographerProfile profile = repository.findById(photographerId)
                .orElseThrow(() -> new NoSuchElementException("fotógrafo não encontrado"));

        return new ProfileResponse(
                profile.name(),
                profile.email(),
                profile.whatsapp(),
                profile.timezone());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void updateProfile(UUID photographerId, ProfileUpdateRequest request) {
        // Must be the first statement in the transaction so RLS applies to everything after it.
        repository.setTenantContext(photographerId);

        if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) {
            throw new TimezoneInvalidaException();
        }

        String whatsappNormalizado = WhatsappNormalizer.normalize(request.whatsapp());

        String whatsappToStore = request.whatsapp() == null || request.whatsapp().trim().isEmpty()
                ? null
                : request.whatsapp().trim();

        repository.update(
                photographerId,
                request.name().trim(),
                whatsappToStore,
                whatsappNormalizado,
                request.timezone());
    }
}
