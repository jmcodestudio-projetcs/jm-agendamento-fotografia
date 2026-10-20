package com.jmcodestudio.agendamento.signup;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlatformSettingsService {

    private final JdbcTemplate jdbcTemplate;

    public PlatformSettingsService(@Qualifier("signupDataSource") DataSource signupDataSource) {
        this.jdbcTemplate = new JdbcTemplate(signupDataSource);
    }

    public int getIntSetting(String key) {
        List<Integer> values = jdbcTemplate.queryForList(
                "SELECT integer_value FROM scheduling.platform_settings WHERE setting_key = ?",
                Integer.class, key);
        if (values.isEmpty()) {
            throw new IllegalStateException(
                    "Configuração obrigatória ausente em platform_settings: '" + key + "'");
        }
        return values.get(0);
    }
}
