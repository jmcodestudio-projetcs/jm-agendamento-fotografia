package com.jmcodestudio.agendamento.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * DataSource dedicado à recuperação de acesso (reset de senha), com role de banco
 * própria de privilégio mínimo (ver claude/fase-1.3-recuperacao-acesso.md, decisão 4).
 * Propositalmente não é @Primary: o DataSource principal continua sendo o padrão
 * do Spring Boot.
 */
@Configuration
public class PasswordResetDataSourceConfig {

    @Bean
    @Qualifier("resetDataSource")
    public DataSource resetDataSource(
            @Value("jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:agendamento_fotografia}") String url,
            @Value("${DB_RESET_USER:agendamento_reset}") String username,
            @Value("${DB_RESET_PASSWORD:changeit_reset}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("reset-pool");
        return dataSource;
    }

    @Bean
    @Qualifier("resetTransactionManager")
    public DataSourceTransactionManager resetTransactionManager(
            @Qualifier("resetDataSource") DataSource resetDataSource) {
        return new DataSourceTransactionManager(resetDataSource);
    }
}
