package com.jmcodestudio.agendamento.profile;

import java.time.ZoneId;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;

import com.jmcodestudio.agendamento.profile.exception.SlugInvalidoException;
import com.jmcodestudio.agendamento.profile.exception.SlugJaEmUsoException;
import com.jmcodestudio.agendamento.signup.exception.TimezoneInvalidaException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotographerProfileService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int SLUG_MIN_LENGTH = 3;
    private static final int SLUG_MAX_LENGTH = 100;

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
                profile.timezone(),
                profile.publicSlug());
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

    @Transactional(transactionManager = "tenantTransactionManager")
    public void updatePublicSlug(UUID photographerId, SlugUpdateRequest request) {
        // Must be the first statement in the transaction so RLS applies to everything after it.
        repository.setTenantContext(photographerId);

        String candidato = request.slug().trim().toLowerCase(Locale.ROOT);

        if (candidato.length() < SLUG_MIN_LENGTH
                || candidato.length() > SLUG_MAX_LENGTH
                || !SLUG_PATTERN.matcher(candidato).matches()) {
            throw new SlugInvalidoException();
        }

        try {
            repository.updateSlug(photographerId, candidato);
        } catch (DataIntegrityViolationException e) {
            if (violatedConstraint(e).contains("slug")) {
                throw new SlugJaEmUsoException();
            }
            throw e;
        }
    }

    private static String violatedConstraint(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                String constraint = psql.getServerErrorMessage().getConstraint();
                if (constraint != null) {
                    return constraint.toLowerCase(Locale.ROOT);
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        String message = e.getMostSpecificCause().getMessage();
        return message == null ? "" : message.toLowerCase(Locale.ROOT);
    }
}
