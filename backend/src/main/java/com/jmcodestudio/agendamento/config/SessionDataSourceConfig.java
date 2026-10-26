package com.jmcodestudio.agendamento.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;
import org.springframework.session.jdbc.config.annotation.SpringSessionTransactionManager;

/**
 * DataSource dedicado ao Spring Session JDBC, com role de banco de privilégio
 * mínimo (jm_session, ver V7__session_bootstrap_role.sql). Propositalmente não é
 * @Primary: o DataSource principal continua sendo o padrão do Spring Boot.
 *
 * Necessário porque o projeto tem vários DataSource/PlatformTransactionManager
 * (signup, login, reset, worker, principal) e o auto-config do Spring Session
 * (JdbcHttpSessionConfiguration) exige um candidato único e sem ambiguidade -
 * daqui os qualifiers @SpringSessionDataSource/@SpringSessionTransactionManager,
 * que são os únicos que esse auto-config reconhece.
 */
@Configuration
public class SessionDataSourceConfig {

    @Bean
    @SpringSessionDataSource
    public DataSource sessionDataSource(
            @Value("jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:agendamento_fotografia}") String url,
            @Value("${DB_SESSION_USER:agendamento_session}") String username,
            @Value("${DB_SESSION_PASSWORD:changeit_session}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("session-pool");
        return dataSource;
    }

    @Bean
    @SpringSessionTransactionManager
    public DataSourceTransactionManager sessionTransactionManager(
            @Qualifier("sessionDataSource") DataSource sessionDataSource) {
        return new DataSourceTransactionManager(sessionDataSource);
    }
}
