package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ExportacionAuditoria;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Repository;

/** Acceso a la colección {@code exportaciones_auditoria} de MongoDB. */
@Repository
public class ExportacionAuditoriaRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public ExportacionAuditoriaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public ExportacionAuditoria insertar(ExportacionAuditoria exportacion) {
        return mongoTemplate.insert(exportacion);
    }

    /** Una exportación solo la ve quien la pidió. */
    public Optional<ExportacionAuditoria> findDelUsuario(Long id, Long empresaId, Long usuarioId) {
        if (id == null || empresaId == null || usuarioId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(query(where("id").is(id)
                .and("empresaId").is(empresaId).and("usuarioId").is(usuarioId)), ExportacionAuditoria.class));
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        mongoTemplate.indexOps(ExportacionAuditoria.class).ensureIndex(new Index()
                .on("empresaId", Sort.Direction.ASC).on("usuarioId", Sort.Direction.ASC)
                .on("creadoEn", Sort.Direction.DESC).named("idx_exportaciones_auditoria_empresa_usuario"));
    }
}
