package com.jmcodestudio.agendamento.login;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jmcodestudio.agendamento.config.AbsoluteSessionTimeoutFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
public class PhotographerAuthenticationController {

    private final PhotographerLoginService photographerLoginService;
    private final SecurityContextRepository securityContextRepository;

    public PhotographerAuthenticationController(PhotographerLoginService photographerLoginService,
            SecurityContextRepository securityContextRepository) {
        this.photographerLoginService = photographerLoginService;
        this.securityContextRepository = securityContextRepository;
    }

    // Não é código morto: sendo um GET que passa pelo filtro de CSRF do Spring Security,
    // faz o cookie XSRF-TOKEN ser gravado para o frontend usar nos POSTs seguintes
    // (claude/fase-1.2-login-fotografo.md, decisão 3).
    @GetMapping("/api/public/csrf-token")
    public ResponseEntity<Void> csrfToken() {
        return ResponseEntity.noContent().build();
    }

    // Escolha: o service já cobre rate limit e respostas genéricas, então não usamos
    // DaoAuthenticationProvider/UserDetailsService. Em sucesso, montamos o Authentication
    // aqui e o salvamos na sessão (Spring Security 6+ exige saveContext explícito).
    @PostMapping("/api/public/photographers/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginResult result = photographerLoginService.login(
                request.email(), request.password(), httpRequest.getRemoteAddr());

        // Proteção contra session fixation: troca o id de uma sessão pré-existente.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        UserDetails principal = new User(result.photographerId().toString(), "",
                List.of(new SimpleGrantedAuthority("ROLE_PHOTOGRAPHER")));
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        httpRequest.getSession().setAttribute(AbsoluteSessionTimeoutFilter.LOGIN_AT_ATTRIBUTE, Instant.now());

        return ResponseEntity.ok(new LoginResponse(result.photographerId(), result.name()));
    }

    @PostMapping("/api/photographers/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
