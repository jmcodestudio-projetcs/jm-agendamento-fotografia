package com.jmcodestudio.agendamento.login;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jmcodestudio.agendamento.login.exception.AccountSecurityBlockedException;
import com.jmcodestudio.agendamento.login.exception.InvalidCredentialsException;
import com.jmcodestudio.agendamento.login.exception.LoginDeniedException;
import com.jmcodestudio.agendamento.login.exception.LoginRateLimitedException;
import com.jmcodestudio.agendamento.signup.PlatformSettingsService;

@Service
public class PhotographerLoginService {

    private static final String SCOPE = "LOGIN";

    // Hash Argon2id fixo (mesmos parâmetros do PasswordEncoder do projeto). Comparar contra ele
    // quando o e-mail não existe iguala o tempo de resposta e evita enumeração de contas.
    private static final String DUMMY_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$6Ad4xa4u/UWE+2ePlCJSWQ$7L9OMgVnAkoDqbwpVQd2EpRJNlg+AE3jyYWdol3n3Lg";

    private final LoginCredentialRepository loginCredentialRepository;
    private final AccessAttemptRepository accessAttemptRepository;
    private final AccessAttemptHmac accessAttemptHmac;
    private final PasswordEncoder passwordEncoder;
    private final PlatformSettingsService platformSettingsService;

    public PhotographerLoginService(
            LoginCredentialRepository loginCredentialRepository,
            AccessAttemptRepository accessAttemptRepository,
            AccessAttemptHmac accessAttemptHmac,
            PasswordEncoder passwordEncoder,
            PlatformSettingsService platformSettingsService) {
        this.loginCredentialRepository = loginCredentialRepository;
        this.accessAttemptRepository = accessAttemptRepository;
        this.accessAttemptHmac = accessAttemptHmac;
        this.passwordEncoder = passwordEncoder;
        this.platformSettingsService = platformSettingsService;
    }

    // noRollbackFor: o registro da tentativa falha precisa commitar junto com a negação.
    @Transactional(transactionManager = "loginTransactionManager", noRollbackFor = LoginDeniedException.class)
    public LoginResult login(String email, String password, String clientIp) {
        String emailNormalized = email.trim().toLowerCase(Locale.ROOT);
        byte[] subjectKey = accessAttemptHmac.hmac(emailNormalized);
        byte[] originKey = accessAttemptHmac.hmac(clientIp);

        int failuresPerAccount = platformSettingsService.getIntSetting("login_failures_per_account");
        int windowSeconds = platformSettingsService.getIntSetting("login_window_seconds");
        int attemptsPerIp = platformSettingsService.getIntSetting("login_attempts_per_ip");

        if (accessAttemptRepository.countRecentSubjectFailures(SCOPE, subjectKey, windowSeconds)
                >= failuresPerAccount
                || accessAttemptRepository.countRecentOriginFailures(SCOPE, originKey, windowSeconds)
                        >= attemptsPerIp) {
            recordFailure(null, subjectKey, originKey);
            throw new LoginRateLimitedException();
        }

        LoginCredential credential = loginCredentialRepository.findByEmailNormalized(emailNormalized)
                .orElse(null);

        if (credential == null) {
            passwordEncoder.matches(password, DUMMY_HASH);
            recordFailure(null, subjectKey, originKey);
            throw new InvalidCredentialsException();
        }

        if (credential.securityBlockedAt() != null) {
            recordFailure(credential.photographerId(), subjectKey, originKey);
            throw new AccountSecurityBlockedException();
        }

        if (credential.passwordHash() == null
                || !passwordEncoder.matches(password, credential.passwordHash())) {
            recordFailure(credential.photographerId(), subjectKey, originKey);
            throw new InvalidCredentialsException();
        }

        accessAttemptRepository.recordAttempt(SCOPE, credential.photographerId(), subjectKey, originKey, true);
        loginCredentialRepository.touchLastLogin(credential.photographerUserId());
        return new LoginResult(credential.photographerId(), credential.photographerName());
    }

    private void recordFailure(UUID photographerId, byte[] subjectKey, byte[] originKey) {
        accessAttemptRepository.recordAttempt(SCOPE, photographerId, subjectKey, originKey, false);
    }
}
