package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EventoAuditoria;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Bitácora de auditoría en la colección {@code auditoria_eventos}.
 *
 * <p>A propósito solo ofrece insertar y consultar: la bitácora es inmutable.</p>
 */
@Repository
public class EventoAuditoriaRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public EventoAuditoriaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public EventoAuditoria insertar(EventoAuditoria evento) {
        return mongoTemplate.insert(evento);
    }

    public Optional<EventoAuditoria> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), EventoAuditoria.class));
    }

    public Page<EventoAuditoria> buscar(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), EventoAuditoria.class);
        return new PageImpl<>(mongoTemplate.find(new Query(filtro).with(pagina), EventoAuditoria.class),
                pagina, total);
    }

    /** Eventos con esa acción hechos por el actor desde la fecha indicada. */
    public long contarAcciones(Long actorId, String accion, LocalDateTime desde) {
        return mongoTemplate.count(query(where("actorId").is(actorId).and("accion").is(accion)
                .and("ocurridoEn").gte(desde)), EventoAuditoria.class);
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(EventoAuditoria.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("ocurridoEn", Sort.Direction.DESC)
                .named("idx_auditoria_eventos_empresa_fecha"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("modulo", Sort.Direction.ASC)
                .on("accion", Sort.Direction.ASC).on("ocurridoEn", Sort.Direction.DESC)
                .named("idx_auditoria_eventos_empresa_modulo_accion"));
        indices.ensureIndex(new Index().on("actorId", Sort.Direction.ASC).on("ocurridoEn", Sort.Direction.DESC)
                .named("idx_auditoria_eventos_actor_fecha"));
        indices.ensureIndex(new Index().on("entidadTipo", Sort.Direction.ASC).on("entidadId", Sort.Direction.ASC)
                .named("idx_auditoria_eventos_entidad"));
    }
}
