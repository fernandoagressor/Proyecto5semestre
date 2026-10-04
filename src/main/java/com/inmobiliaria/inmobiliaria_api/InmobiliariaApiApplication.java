package com.inmobiliaria.inmobiliaria_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class InmobiliariaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InmobiliariaApiApplication.class, args);
    }
}
