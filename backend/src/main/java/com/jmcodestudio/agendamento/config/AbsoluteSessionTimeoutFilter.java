package com.jmcodestudio.agendamento.config;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jmcodestudio.agendamento.signup.PlatformSettingsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Encerra a sessão do fotógrafo quando o tempo absoluto desde o login estoura.
 * Não escreve resposta: limpa sessão e contexto e segue a cadeia, de modo que a
 * autorização do Spring Security nega a requisição como não autenticada (401).
 * Deliberadamente não é @Component, para não ser registrado duas vezes (servlet
 * container + cadeia de segurança); é instanciado no SecurityConfig.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    public static final String LOGIN_AT_ATTRIBUTE = "LOGIN_AT";

    private static final String SETTING_KEY = "photographer_absolute_seconds";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final PlatformSettingsService platformSettingsService;

    // Abordagem escolhida: cache em memória com TTL de 60s, para não fazer uma query
    // em platform_settings a cada requisição. Mudanças no valor levam até 60s para valer.
    private volatile int cachedAbsoluteSeconds;
    private volatile Instant cachedAt = Instant.MIN;

    public AbsoluteSessionTimeoutFilter(PlatformSettingsService platformSettingsService) {
        this.platformSettingsService = platformSettingsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(LOGIN_AT_ATTRIBUTE) instanceof Instant loginAt) {
            long elapsedSeconds = Duration.between(loginAt, Instant.now()).getSeconds();
            if (elapsedSeconds > absoluteSeconds()) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private int absoluteSeconds() {
        Instant now = Instant.now();
        if (Duration.between(cachedAt, now).compareTo(CACHE_TTL) >= 0) {
            cachedAbsoluteSeconds = platformSettingsService.getIntSetting(SETTING_KEY);
            cachedAt = now;
        }
        return cachedAbsoluteSeconds;
    }
}
