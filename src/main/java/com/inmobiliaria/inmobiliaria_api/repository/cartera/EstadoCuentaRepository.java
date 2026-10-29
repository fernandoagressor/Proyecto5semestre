package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuentaGenerado;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Repository;

/** Acceso a la colección {@code estados_cuenta} de MongoDB. Solo inserta. */
@Repository
public class EstadoCuentaRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public EstadoCuentaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Inserta; falla con {@code DuplicateKeyException} (409) si ya hay uno con ese contrato y corte. */
    public EstadoCuentaGenerado insertar(EstadoCuentaGenerado estado) {
        return mongoTemplate.insert(estado);
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(EstadoCuentaGenerado.class);
        indices.ensureIndex(new Index().on("contratoId", Sort.Direction.ASC).on("fechaCorte", Sort.Direction.ASC)
                .unique().named("uk_estados_cuenta_contrato_corte"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("fechaCorte", Sort.Direction.DESC)
                .named("idx_estados_cuenta_empresa_corte"));
    }
}
