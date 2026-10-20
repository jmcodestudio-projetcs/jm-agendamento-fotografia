package com.jmcodestudio.agendamento.signup;

import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.jmcodestudio.agendamento.signup.exception.EmailJaCadastradoException;
import com.jmcodestudio.agendamento.signup.exception.InvalidPasswordException;
import com.jmcodestudio.agendamento.signup.exception.TimezoneInvalidaException;

@Service
public class PhotographerSignupService {

    private static final int MAX_SLUG_ATTEMPTS = 10;

    private final PhotographerSignupRepository repository;
    private final PlatformSettingsService settings;
    private final CommonPasswordChecker commonPasswordChecker;
    private final PasswordEncoder passwordEncoder;

    public PhotographerSignupService(PhotographerSignupRepository repository,
            PlatformSettingsService settings,
            CommonPasswordChecker commonPasswordChecker,
            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.settings = settings;
        this.commonPasswordChecker = commonPasswordChecker;
        this.passwordEncoder = passwordEncoder;
    }

    public UUID register(SignupRequest request) {
        if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) {
            throw new TimezoneInvalidaException();
        }

        int min = settings.getIntSetting("password_min_chars");
        int max = settings.getIntSetting("password_max_chars");
        int length = request.password().length();
        if (length < min || length > max) {
            throw new InvalidPasswordException(
                    "a senha deve ter entre " + min + " e " + max + " caracteres");
        }

        if (commonPasswordChecker.isCommon(request.password())) {
            throw new InvalidPasswordException("senha muito comum, escolha outra");
        }

        String emailOriginal = request.email().trim();
        String emailNormalized = emailOriginal.toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(request.password());
        String slugBase = SlugGenerator.slugify(request.name());

        for (int attempt = 1; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
            String slugCandidate = SlugGenerator.withSuffix(slugBase, attempt);
            try {
                return repository.insertNewPhotographer(request.name(), emailOriginal,
                        emailNormalized, passwordHash, request.timezone(), slugCandidate);
            } catch (DataIntegrityViolationException e) {
                String constraint = violatedConstraint(e);
                if (constraint.contains("email")) {
                    throw new EmailJaCadastradoException();
                }
                if (!constraint.contains("slug")) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException(
                "não foi possível gerar um slug único após " + MAX_SLUG_ATTEMPTS + " tentativas");
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
