package com.inmobiliaria.inmobiliaria_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(
        boolean enabled,
        String empresaCodigo,
        String empresaNombre,
        String empresaNit,
        String documento,
        String nombres,
        String apellidos,
        String username,
        String email,
        String password
) {
}
