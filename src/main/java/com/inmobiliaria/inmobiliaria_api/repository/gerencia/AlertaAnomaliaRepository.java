package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.AlertaAnomalia;
import java.util.List;
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

/** Acceso a la colección {@code alertas_anomalia} de MongoDB. */
@Repository
public class AlertaAnomaliaRepository {

    private final MongoTemplate mongoTemplate;

    public AlertaAnomaliaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AlertaAnomalia insertar(AlertaAnomalia alerta) {
        return mongoTemplate.insert(alerta);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro la guardó antes. */
    public AlertaAnomalia guardar(AlertaAnomalia alerta) {
        return mongoTemplate.save(alerta);
    }

    public Optional<AlertaAnomalia> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), AlertaAnomalia.class));
    }

    public Page<AlertaAnomalia> buscar(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), AlertaAnomalia.class);
        return new PageImpl<>(mongoTemplate.find(new Query(filtro).with(pagina), AlertaAnomalia.class),
                pagina, total);
    }

    /** Si ya hay una alerta sin cerrar de ese tipo para ese usuario en la empresa. */
    public boolean existeAbierta(Long empresaId, String tipo, Long usuarioId) {
        return mongoTemplate.exists(query(where("empresaId").is(empresaId).and("tipo").is(tipo)
                .and("usuarioId").is(usuarioId)
                .and("estado").in(List.of(AlertaAnomalia.ABIERTA, AlertaAnomalia.EN_INVESTIGACION))),
                AlertaAnomalia.class);
    }

    /** Crea los índices de la colección; es idempotente. */
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(AlertaAnomalia.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .on("creadoEn", Sort.Direction.DESC).named("idx_alertas_anomalia_empresa_estado"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("tipo", Sort.Direction.ASC)
                .on("usuarioId", Sort.Direction.ASC).named("idx_alertas_anomalia_empresa_tipo_usuario"));
    }
}
