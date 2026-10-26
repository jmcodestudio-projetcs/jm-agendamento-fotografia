package com.jmcodestudio.agendamento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgendamentoFotografiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgendamentoFotografiaApplication.class, args);
    }
}
