package com.inmobiliaria.inmobiliaria_api.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Declara los dos gestores de transacción de la persistencia políglota.
 *
 * <p>JPA (MySQL) es el gestor primario: todo {@code @Transactional} sin nombre
 * sigue operando sobre MySQL, como antes. MongoDB debe pedirse explícitamente con
 * {@code @Transactional(TransaccionesConfig.MONGO)}.</p>
 *
 * <p>Ambos se declaran de forma explícita porque Spring Boot deja de crear el
 * gestor de JPA en cuanto existe cualquier otro gestor de transacciones.</p>
 */
@Configuration
public class TransaccionesConfig {

    public static final String MONGO = "mongoTransactionManager";

    @Bean(name = "transactionManager")
    @Primary
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = MONGO)
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }
}