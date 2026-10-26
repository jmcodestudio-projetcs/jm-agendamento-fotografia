package com.jmcodestudio.agendamento.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * DataSource dedicado ao worker, com role de banco própria de privilégio mínimo
 * (ver claude/fase-1.3-recuperacao-acesso.md, decisão 5). Propositalmente não é
 * @Primary: o DataSource principal continua sendo o padrão do Spring Boot.
 */
@Configuration
public class WorkerDataSourceConfig {

    @Bean
    @Qualifier("workerDataSource")
    public DataSource workerDataSource(
            @Value("jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:agendamento_fotografia}") String url,
            @Value("${DB_WORKER_USER:agendamento_worker}") String username,
            @Value("${DB_WORKER_PASSWORD:changeit_worker}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("worker-pool");
        return dataSource;
    }

    @Bean
    @Qualifier("workerTransactionManager")
    public DataSourceTransactionManager workerTransactionManager(
            @Qualifier("workerDataSource") DataSource workerDataSource) {
        return new DataSourceTransactionManager(workerDataSource);
    }
}
