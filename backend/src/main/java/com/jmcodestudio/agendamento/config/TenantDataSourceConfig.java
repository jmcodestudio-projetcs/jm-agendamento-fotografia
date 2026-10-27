package com.jmcodestudio.agendamento.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * DataSource dedicado a operações autenticadas e tenant-scoped (RLS via
 * app.photographer_id), com role de banco de privilégio mínimo (jm_tenant,
 * já definida desde a V1, com GRANTs ampliados ao longo das fases
 * conforme cada funcionalidade autenticada precisa). Propositalmente não é
 * @Primary: o DataSource principal continua sendo o padrão do Spring Boot.
 *
 * Usado pela primeira vez na 1.4 (perfil do fotógrafo) e pensado para ser
 * reutilizado por toda funcionalidade futura que opere sobre dados
 * pertencentes a um fotógrafo autenticado (agenda, serviços, bloqueios,
 * etc.), sempre com o contexto RLS setado via SET LOCAL dentro da mesma
 * transação (ver TenantContext / serviços que o utilizam).
 */
@Configuration
public class TenantDataSourceConfig {

    @Bean
    @Qualifier("tenantDataSource")
    public DataSource tenantDataSource(
            @Value("jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:agendamento_fotografia}") String url,
            @Value("${DB_TENANT_USER:agendamento_tenant}") String username,
            @Value("${DB_TENANT_PASSWORD:changeit_tenant}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("tenant-pool");
        return dataSource;
    }

    @Bean
    @Qualifier("tenantTransactionManager")
    public DataSourceTransactionManager tenantTransactionManager(
            @Qualifier("tenantDataSource") DataSource tenantDataSource) {
        return new DataSourceTransactionManager(tenantDataSource);
    }
}
