package com.inmobiliaria.inmobiliaria_api.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Habilita la abstracción de caché de Spring. Redis es el proveedor configurado
 * en application.yml; ningún dato de negocio debe existir únicamente en caché.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {
    public static final String DASHBOARDS = "dashboards";
    public static final String TOKEN_VERSION ="token-version";

    @Bean
    RedisCacheManagerBuilderCustomizer nexoraCore(){
        return builder -> builder
                .withCacheConfiguration(DASHBOARDS, base(Duration.ofMinutes(5)))
                .withCacheConfiguration(TOKEN_VERSION, base(Duration.ofMinutes(15)))
                .transactionAware();
    }
    private static RedisCacheConfiguration base(Duration ttl){
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("nexora:")
                .entryTtl(ttl)
                .disableCachingNullValues();

    }
}
