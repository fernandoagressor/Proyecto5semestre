package com.inmobiliaria.inmobiliaria_api.repository.simulacion;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
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

/** Acceso a la colección {@code simulaciones} de MongoDB. */
@Repository
public class SimulacionRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public SimulacionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<Simulacion> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), Simulacion.class));
    }

    public Simulacion insertar(Simulacion simulacion) {
        return mongoTemplate.insert(simulacion);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro la guardó antes. */
    public Simulacion guardar(Simulacion simulacion) {
        simulacion.tocar();
        return mongoTemplate.save(simulacion);
    }

    /** Página sin el cronograma; los objetos no se deben guardar. */
    public Page<Simulacion> buscarResumen(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), Simulacion.class);
        Query consulta = new Query(filtro).with(pagina);
        consulta.fields().exclude("cuotas");
        return new PageImpl<>(mongoTemplate.find(consulta, Simulacion.class), pagina, total);
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(Simulacion.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("codigo", Sort.Direction.ASC)
                .unique().named("uk_simulaciones_empresa_codigo"));
        indices.ensureIndex(new Index().on("oportunidadId", Sort.Direction.ASC).on("creadoEn", Sort.Direction.DESC)
                .named("idx_simulaciones_oportunidad"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .on("creadoEn", Sort.Direction.DESC).named("idx_simulaciones_empresa_estado"));
    }
}
