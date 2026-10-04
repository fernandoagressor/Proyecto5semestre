package com.inmobiliaria.inmobiliaria_api.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String issuer,
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        List<String> allowedOrigins,
        int maxFailedAttempts,
        Duration lockDuration
) {
}
