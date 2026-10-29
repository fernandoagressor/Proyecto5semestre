package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ReporteGenerado;
import java.time.Duration;
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
 * Acceso a la colección {@code reportes_generados} de MongoDB. MongoDB borra cada solicitud
 * {@link #RETENCION} después de creada (índice TTL); el archivo en MySQL no se toca.
 */
@Repository
public class ReporteGeneradoRepository {

    /** Tiempo que se conserva cada solicitud de reporte. */
    public static final Duration RETENCION = Duration.ofDays(90);

    private final MongoTemplate mongoTemplate;

    public ReporteGeneradoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public ReporteGenerado insertar(ReporteGenerado reporte) {
        return mongoTemplate.insert(reporte);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro lo guardó antes. */
    public ReporteGenerado guardar(ReporteGenerado reporte) {
        return mongoTemplate.save(reporte);
    }

    public Optional<ReporteGenerado> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), ReporteGenerado.class));
    }

    public Page<ReporteGenerado> buscar(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), ReporteGenerado.class);
        return new PageImpl<>(mongoTemplate.find(new Query(filtro).with(pagina), ReporteGenerado.class),
                pagina, total);
    }

    /** Crea los índices de la colección (incluido el TTL de {@link #RETENCION}); es idempotente. */
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(ReporteGenerado.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .on("creadoEn", Sort.Direction.DESC).named("idx_reportes_generados_empresa_estado"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("proyectoId", Sort.Direction.ASC)
                .on("creadoEn", Sort.Direction.DESC).named("idx_reportes_generados_empresa_proyecto"));
        indices.ensureIndex(new Index().on("creadoEn", Sort.Direction.ASC)
                .expire(RETENCION).named("ttl_reportes_generados_90_dias"));
    }
}
