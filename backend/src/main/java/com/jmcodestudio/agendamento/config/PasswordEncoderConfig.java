package com.jmcodestudio.agendamento.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    // Default seguro recomendado pela OWASP, não o valor medido final: os parâmetros de custo
    // definitivos ainda serão medidos na infraestrutura (ver claude/erros-conhecidos.md e
    // claude/fase-1.1-cadastro-fotografo.md).
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
