package com.jmcodestudio.agendamento.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * DataSource dedicado ao login do fotógrafo (JDBC puro, sem JPA), com role de banco
 * de privilégio mínimo. Propositalmente não é @Primary: o DataSource principal
 * continua sendo o padrão do Spring Boot.
 */
@Configuration
public class LoginDataSourceConfig {

    @Bean
    @Qualifier("loginDataSource")
    public DataSource loginDataSource(
            @Value("jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:agendamento_fotografia}") String url,
            @Value("${DB_LOGIN_USER:agendamento_login}") String username,
            @Value("${DB_LOGIN_PASSWORD:changeit_login}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("login-pool");
        return dataSource;
    }

    @Bean
    @Qualifier("loginTransactionManager")
    public DataSourceTransactionManager loginTransactionManager(
            @Qualifier("loginDataSource") DataSource loginDataSource) {
        return new DataSourceTransactionManager(loginDataSource);
    }
}
