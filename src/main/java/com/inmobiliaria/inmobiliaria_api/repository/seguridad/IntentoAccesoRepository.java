package com.inmobiliaria.inmobiliaria_api.repository.seguridad;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Repository;

/** Historial de intentos de acceso en la colección {@code intentos_acceso}; solo se inserta. */
@Repository
public class IntentoAccesoRepository {

    private final MongoTemplate mongoTemplate;

    public IntentoAccesoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public IntentoAcceso insertar(IntentoAcceso intento) {
        return mongoTemplate.insert(intento);
    }

    /** Crea los índices de la colección; es idempotente. */
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(IntentoAcceso.class);
        indices.ensureIndex(new Index().on("identificador", Sort.Direction.ASC)
                .on("fechaIntento", Sort.Direction.DESC).named("idx_intentos_acceso_identificador_fecha"));
        indices.ensureIndex(new Index().on("ipOrigen", Sort.Direction.ASC)
                .on("fechaIntento", Sort.Direction.DESC).named("idx_intentos_acceso_ip_fecha"));
        indices.ensureIndex(new Index().on("usuarioId", Sort.Direction.ASC)
                .on("fechaIntento", Sort.Direction.DESC).named("idx_intentos_acceso_usuario_fecha"));
    }
}
