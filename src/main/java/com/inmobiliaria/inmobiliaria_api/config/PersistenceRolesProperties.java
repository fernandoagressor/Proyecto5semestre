package com.inmobiliaria.inmobiliaria_api.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Declara la responsabilidad de cada tecnología dentro de la persistencia
 * políglota de Nexora. Esta configuración evita tratar Redis como fuente
 * permanente o confundir MySQL con el destino documental.
 */
@Validated
@ConfigurationProperties(prefix = "app.persistence")
public record PersistenceRolesProperties(
        @NotNull Store primaryStore,
        @NotNull Store transactionalStore,
        @NotNull Store ephemeralStore
) {

    public enum Store {
        MONGODB,
        MYSQL,
        REDIS
    }

    @AssertTrue(message = "Cada responsabilidad de persistencia debe usar una tecnología diferente")
    public boolean isAssignmentUnambiguous() {
        return primaryStore != transactionalStore
                && primaryStore != ephemeralStore
                && transactionalStore != ephemeralStore;
    }
}
