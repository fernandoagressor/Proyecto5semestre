package com.inmobiliaria.inmobiliaria_api.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita la abstracción de caché de Spring. Redis es el proveedor configurado
 * en application.yml; ningún dato de negocio debe existir únicamente en caché.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {
}
