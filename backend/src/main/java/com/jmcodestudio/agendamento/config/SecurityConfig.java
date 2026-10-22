package com.jmcodestudio.agendamento.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.jmcodestudio.agendamento.signup.PlatformSettingsService;

@Configuration
public class SecurityConfig {

    // Origens do frontend autorizadas a chamar a API via browser (CORS).
    // Separadas por virgula. Default cobre o Vite local (npm run dev).
    // Em staging/producao, sobrescrever via env var com o(s) dominio(s)
    // reais do frontend publicado.
    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            PlatformSettingsService platformSettingsService) throws Exception {
        http
                .addFilterBefore(new AbsoluteSessionTimeoutFilter(platformSettingsService),
                        AuthorizationFilter.class)
                .cors(Customizer.withDefaults())
                // Só o cadastro é isento de CSRF; o login exige o token (decisão 3 da fase 1.2).
                // CsrfTokenRequestAttributeHandler com atributo null desliga o token diferido,
                // então o cookie XSRF-TOKEN é gravado já no GET /api/public/csrf-token.
                .csrf(csrf -> {
                    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
                    csrfHandler.setCsrfRequestAttributeName(null);
                    csrf.ignoringRequestMatchers("/api/public/photographers/signup")
                            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                            .csrfTokenRequestHandler(csrfHandler);
                })
                // Repositório explícito: o Spring Security 6+ não persiste o contexto na sessão
                // sozinho; o controller de login salva o Authentication por ele.
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/public/csrf-token").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/photographers/login").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // Sem isto, uma falha interna (500) num endpoint publico aparece
                        // para o cliente como 401, porque o forward para /error tambem
                        // cai em "anyRequest().authenticated()". Ver claude/erros-conhecidos.md #3.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(corsAllowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
