package com.jmcodestudio.agendamento.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * DataSource dedicado à leitura pública (página pública de agendamento,
 * Fase 4.1), com role de privilégio mínimo jm_public (V10) — só SELECT,
 * nunca escrita. Diferente de jm_tenant (sessão do fotógrafo) e jm_client
 * (token do cliente já emitido), jm_public não exige nenhuma credencial:
 * é a primeira leitura verdadeiramente anônima do sistema. Propositalmente
 * não é @Primary.
 */
@Configuration
public class PublicDataSourceConfig {

    @Bean
    @Qualifier("publicDataSource")
    public DataSource publicDataSource(
            @Value("jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:agendamento_fotografia}") String url,
            @Value("${DB_PUBLIC_USER:agendamento_public}") String username,
            @Value("${DB_PUBLIC_PASSWORD:changeit_public}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("public-pool");
        return dataSource;
    }

    @Bean
    @Qualifier("publicTransactionManager")
    public DataSourceTransactionManager publicTransactionManager(
            @Qualifier("publicDataSource") DataSource publicDataSource) {
        return new DataSourceTransactionManager(publicDataSource);
    }
}
