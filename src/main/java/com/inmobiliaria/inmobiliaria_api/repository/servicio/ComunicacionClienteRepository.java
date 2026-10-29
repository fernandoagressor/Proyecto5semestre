package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.ComunicacionCliente;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
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

/** Acceso a la colección {@code comunicaciones_cliente} de MongoDB. */
@Repository
public class ComunicacionClienteRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public ComunicacionClienteRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<ComunicacionCliente> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), ComunicacionCliente.class));
    }

    public ComunicacionCliente insertar(ComunicacionCliente comunicacion) {
        return mongoTemplate.insert(comunicacion);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro la guardó antes. */
    public ComunicacionCliente guardar(ComunicacionCliente comunicacion) {
        comunicacion.tocar();
        return mongoTemplate.save(comunicacion);
    }

    public Page<ComunicacionCliente> buscar(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), ComunicacionCliente.class);
        return new PageImpl<>(mongoTemplate.find(new Query(filtro).with(pagina), ComunicacionCliente.class),
                pagina, total);
    }

    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(ComunicacionCliente.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("creadoEn", Sort.Direction.DESC)
                .named("idx_comunicaciones_empresa_creado"));
        indices.ensureIndex(new Index().on("estado", Sort.Direction.ASC).on("programadaPara", Sort.Direction.ASC)
                .named("idx_comunicaciones_estado_programada"));
        indices.ensureIndex(new Index().on("clienteId", Sort.Direction.ASC).on("creadoEn", Sort.Direction.DESC)
                .named("idx_comunicaciones_cliente_creado"));
    }
}
