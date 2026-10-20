package com.jmcodestudio.agendamento.signup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Verifica senhas contra uma lista local curada de senhas comuns
 * ({@code classpath:signup/common-passwords.txt}, uma por linha).
 *
 * <p>Solução provisória: não é checagem contra vazamentos reais (como o
 * HaveIBeenPwned). O mecanismo final ainda não foi decidido na baseline
 * (ver claude/fase-1.1-cadastro-fotografo.md).
 */
@Component
public class CommonPasswordChecker {

    private static final String RESOURCE_PATH = "signup/common-passwords.txt";

    private final Set<String> commonPasswords;

    public CommonPasswordChecker() {
        this.commonPasswords = load();
    }

    public boolean isCommon(String password) {
        if (password == null) {
            return false;
        }
        return commonPasswords.contains(normalize(password));
    }

    private static Set<String> load() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE_PATH).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(CommonPasswordChecker::normalize)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível carregar " + RESOURCE_PATH, e);
        }
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
