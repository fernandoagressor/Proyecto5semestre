package com.inmobiliaria.inmobiliaria_api.service.compartido.migracion;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Crea al arrancar los índices de todas las colecciones de MongoDB (únicos, TTL y de consulta),
 * antes que los migradores. Antes solo los creaba cada migrador, así que con el migrador apagado
 * una colección quedaba sin su índice único. Se apaga con {@code app.mongo.indices.enabled=false}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.mongo.indices", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InicializadorIndicesMongo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorIndicesMongo.class);

    private final List<IndicesMongo> repositorios;

    public InicializadorIndicesMongo(List<IndicesMongo> repositorios) {
        this.repositorios = repositorios;
    }

    @Override
    public void run(ApplicationArguments args) {
        repositorios.forEach(IndicesMongo::asegurarIndices);
        log.info("Índices de MongoDB verificados en {} colecciones", repositorios.size());
    }
}
