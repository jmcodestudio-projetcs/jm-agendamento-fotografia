package com.jmcodestudio.agendamento.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * DataSource dedicado ao cadastro público (JDBC puro, sem JPA), com role de banco
 * de privilégio mínimo. Propositalmente não é @Primary: o DataSource principal
 * continua sendo o padrão do Spring Boot.
 */
@Configuration
public class SignupDataSourceConfig {

    @Bean
    @Qualifier("signupDataSource")
    public DataSource signupDataSource(
            @Value("jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:agendamento_fotografia}") String url,
            @Value("${DB_SIGNUP_USER:agendamento_signup}") String username,
            @Value("${DB_SIGNUP_PASSWORD:changeit_signup}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("signup-pool");
        return dataSource;
    }

    @Bean
    @Qualifier("signupTransactionManager")
    public DataSourceTransactionManager signupTransactionManager(
            @Qualifier("signupDataSource") DataSource signupDataSource) {
        return new DataSourceTransactionManager(signupDataSource);
    }
}
